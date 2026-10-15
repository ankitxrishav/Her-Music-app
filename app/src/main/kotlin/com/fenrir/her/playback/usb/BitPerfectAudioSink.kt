@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.fenrir.her.playback.usb

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

class BitPerfectAudioSink(
    val delegate: DefaultAudioSink,
    val usbBitPerfectOutput: UsbBitPerfectOutput? = null,
    val usbDacMonitor: UsbDacMonitor? = null,
) : ForwardingAudioSink(delegate) {

    @Volatile
    var bitPerfectEnabled: Boolean = false
        set(value) {
            field = value
            usbBitPerfectOutput?.setEnabled(value)
            if (value) {
                applyBitPerfectFormat()
            }
        }

    @Volatile
    var appOutputRateHz: Int = 0
        private set

    @Volatile
    var appVolume: Float = 1f
        private set

    var configuredFormat: Format? = null
        private set

    var onConfiguredFormat: ((sampleRateHz: Int, pcmEncoding: Int, channelCount: Int) -> Unit)? = null

    override fun configure(format: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        configuredFormat = format
        appOutputRateHz = format.sampleRate

        if (bitPerfectEnabled) {
            applyBitPerfectFormat(format)
        }

        super.configure(format, specifiedBufferSize, outputChannels)
        onConfiguredFormat?.invoke(format.sampleRate, format.pcmEncoding, format.channelCount)
    }

    fun applyBitPerfectFormat(targetFormat: Format? = configuredFormat) {
        runCatching {
            val format = targetFormat ?: return
            if (usbBitPerfectOutput == null || format.sampleRate <= 0) return
            val targetDevice = usbDacMonitor?.getConnectedUsbDevice()
            usbBitPerfectOutput.setDevice(targetDevice)

            val channelMask = when (format.channelCount) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                else -> AudioFormat.CHANNEL_OUT_STEREO
            }
            val encoding = when (format.pcmEncoding) {
                C.ENCODING_PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
                C.ENCODING_PCM_24BIT -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                C.ENCODING_PCM_32BIT -> AudioFormat.ENCODING_PCM_32BIT
                else -> AudioFormat.ENCODING_PCM_16BIT
            }
            val pcm = AudioFormat.Builder()
                .setSampleRate(format.sampleRate)
                .setChannelMask(channelMask)
                .setEncoding(encoding)
                .build()
            usbBitPerfectOutput.setFormat(pcm)
            usbBitPerfectOutput.setEnabled(true)
        }
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        super.setAudioAttributes(audioAttributes)
        runCatching {
            val platformAttrs = android.media.AudioAttributes.Builder()
                .setContentType(audioAttributes.contentType)
                .setFlags(audioAttributes.flags)
                .setUsage(audioAttributes.usage)
                .build()
            usbBitPerfectOutput?.setAttributes(platformAttrs)
        }
    }

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        super.setPreferredDevice(audioDeviceInfo)
        if (bitPerfectEnabled && audioDeviceInfo != null) {
            runCatching { usbBitPerfectOutput?.setDevice(audioDeviceInfo) }
        }
    }

    override fun setVolume(volume: Float) {
        appVolume = volume
        super.setVolume(volume)
    }

    override fun release() {
        super.release()
        usbBitPerfectOutput?.release()
    }
}
