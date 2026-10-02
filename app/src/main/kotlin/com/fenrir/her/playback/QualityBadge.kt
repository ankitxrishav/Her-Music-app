package com.fenrir.her.playback

import kotlin.math.roundToInt

data class LiveQualityState(
    val audioCodec: String? = null,
    val bitrateKbps: Int? = null,
    val isLossless: Boolean = false,
    val bitDepth: Int? = null,
    val samplingRateKHz: Double? = null,
)

fun qualityBadgeLabel(state: LiveQualityState): String {
    val spatial = spatialIndicatorLabel(state.audioCodec)
    if (spatial != null) return spatial

    val codec = state.audioCodec
    val flacLike = isFlacLikeCodec(codec) || state.isLossless
    val rate = state.samplingRateKHz ?: inferSamplingRate(state)
    val explicitDepth = state.bitDepth?.takeIf { it > 0 }?.let {
        if (it <= 16 && (rate ?: 0.0) > 48.0) null else it
    }
    val depth = explicitDepth ?: inferBitDepth(state, allowRateGuess = false)

    if (flacLike && depth != null && rate != null && rate > 0.0) {
        return "$depth/${formatSampleRateKHz(rate)}kHz"
    }
    if (flacLike && rate != null && rate > 0.0) {
        return "${formatSampleRateKHz(rate)}kHz FLAC"
    }
    if (flacLike && depth != null) {
        return "$depth-BIT FLAC"
    }
    if (flacLike) {
        val parsed = parseQualityFromCodec(codec)
        if (parsed != null) return parsed
        return codec?.takeIf { it.isNotBlank() && !it.equals("AUDIO", true) } ?: "FLAC"
    }

    if (codec?.equals("MP3 320k", ignoreCase = true) == true) return "MP3 320 kbps"
    if (codec?.uppercase() in GENERIC_AUDIO_LABELS) return "AUDIO"
    if (codec != null && state.bitrateKbps != null) return "${codec.uppercase()} ${state.bitrateKbps} kbps"
    if (codec != null) return codec.uppercase()
    if (state.bitrateKbps != null) return "${state.bitrateKbps} kbps"
    return "AUDIO"
}

fun spatialIndicatorLabel(codec: String?): String? {
    val c = codec?.uppercase().orEmpty()
    if (c.contains("ATMOS")) return "ATMOS"
    if (c.contains("SPATIAL") || c.contains("360")) return "SPATIAL"
    return null
}

fun isFlacLikeCodec(codec: String?): Boolean {
    val c = codec?.uppercase().orEmpty()
    if (c.isBlank()) return false
    if (spatialIndicatorLabel(codec) != null) return false
    return c.contains("FLAC") || c == "LOSSLESS" || c.contains("HI-RES") || c.contains("HI_RES") ||
        Regex("""(?:^|[^\d])(16|24|32)\s*(?:[-_]bit)?\s*[/]\s*(\d{2,3}(?:\.\d+)?)\s*k?""", RegexOption.IGNORE_CASE).containsMatchIn(c)
}

fun formatSampleRateKHz(kHzOrHz: Double): String {
    val kHz = if (kHzOrHz > 1000.0) kHzOrHz / 1000.0 else kHzOrHz
    val rounded = (kHz * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

fun buildLiveQualityFromDecoder(
    sampleMimeType: String?,
    bitrateFromFormat: Int?,
    sampleRateHz: Int?,
    pcmEncoding: Int?,
): LiveQualityState {
    val codecUpper = sampleMimeType?.substringAfter("audio/")?.uppercase().orEmpty()
    val isFlac = codecUpper.contains("FLAC") || codecUpper == "X-FLAC" || codecUpper == "FLAC"
    val isAtmos = codecUpper.contains("EAC3") || codecUpper.contains("E-AC-3") ||
        codecUpper.contains("MHA1") || codecUpper.contains("MHM1")
    val friendlyCodec = when {
        isAtmos && (codecUpper.contains("MHA1") || codecUpper.contains("MHM1")) -> "SPATIAL AUDIO"
        isAtmos -> "DOLBY ATMOS"
        isFlac -> "FLAC"
        codecUpper.contains("OPUS") -> "OPUS"
        codecUpper.contains("MP4A") || codecUpper.contains("AAC") -> "AAC"
        codecUpper.contains("MP3") || codecUpper.contains("MPEG") -> "MP3"
        codecUpper.isNotBlank() -> codecUpper
        else -> null
    }
    val kbps = bitrateFromFormat?.takeIf { it > 0 }?.let { it / 1000 }
    val rateKHz = sampleRateHz?.takeIf { it > 0 }?.let { it.toDouble() / 1000.0 }
    val depth = when (pcmEncoding) {
        2 -> 16
        4 -> 32
        0x40000000 -> 24
        else -> null
    }
    return LiveQualityState(
        audioCodec = friendlyCodec,
        bitrateKbps = kbps,
        isLossless = isFlac,
        bitDepth = depth,
        samplingRateKHz = rateKHz,
    )
}

internal fun inferBitDepth(state: LiveQualityState, allowRateGuess: Boolean = true): Int? {
    val codec = state.audioCodec?.uppercase().orEmpty()
    if (codec.contains("32-BIT") || codec.contains("32BIT") || codec.contains("32/")) return 32
    if (codec.contains("24-BIT") || codec.contains("24BIT") || codec.contains("24/")) return 24
    if (codec.contains("16-BIT") || codec.contains("16BIT") || codec.contains("CD") || codec.contains("16/")) return 16

    val explicit = state.bitDepth?.takeIf { it > 0 }
    if (explicit != null) return explicit

    val rate = state.samplingRateKHz
    val kbps = state.bitrateKbps
    if (rate != null && rate > 0.0 && kbps != null && kbps > 0) {
        val inferred = (kbps * 1000.0 / (rate * 1000.0 * 2.0)).roundToInt()
        when (inferred) {
            in 15..17 -> return 16
            in 23..25 -> return 24
            in 31..33 -> return 32
        }
        if (rate <= 48.0) {
            if (kbps >= 1500) return 24
            if (kbps in 400..1150) return 16
        }
    }
    if (!allowRateGuess) return null
    if (rate != null && rate > 48.0) {
        return if (rate > 192.0) 32 else 24
    }
    return null
}

internal fun inferSamplingRate(state: LiveQualityState): Double? {
    val explicit = state.samplingRateKHz?.takeIf { it > 0.0 }
    if (explicit != null) return explicit

    val codec = state.audioCodec.orEmpty()
    Regex("""(?:^|[^\d])(?:16|24|32)\s*(?:[-_]bit)?\s*/\s*(\d{2,3}(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        .find(codec)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0.0 }
        ?.let { return it }
    val match = Regex("""(?:^|[^\d])(\d{2,3}(?:\.\d+)?)\s*(?:k|khz)?(?:[^\d]|$)""", RegexOption.IGNORE_CASE).find(codec)
    if (match != null) {
        val v = match.groupValues[1].toDoubleOrNull()
        if (v != null) {
            return when {
                v in listOf(44.1, 48.0, 88.2, 96.0, 176.4, 192.0, 352.8, 384.0) -> v
                v > 1000.0 -> v / 1000.0
                else -> null
            }
        }
    }
    return null
}

internal fun parseQualityFromCodec(codec: String?): String? {
    val c = codec.orEmpty()
    if (c.isBlank()) return null
    val match = Regex("""(?:^|[^\d])(16|24|32)\s*(?:[-_]bit)?\s*[/]\s*(\d{2,3}(?:\.\d+)?)\s*k?""", RegexOption.IGNORE_CASE).find(c)
    if (match != null) {
        val depth = match.groupValues[1].toIntOrNull() ?: 16
        val rate = match.groupValues[2].toDoubleOrNull()
        if (rate != null) {
            return "$depth/${formatSampleRateKHz(rate)}kHz"
        }
    }
    return null
}

private val GENERIC_AUDIO_LABELS = setOf("AUDIO", "LOCAL AUDIO", "PCM")
