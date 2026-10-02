@file:Suppress("DEPRECATION")

package com.fenrir.her.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.SQLException
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import org.chromium.net.CronetEngine
import com.fenrir.her.constants.EnableCronetKey
import com.fenrir.her.constants.ForceOpusKey
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import dagger.hilt.android.AndroidEntryPoint
import com.fenrir.her.MainActivity
import com.fenrir.her.R
import com.fenrir.her.constants.AudioNormalizationKey
import com.fenrir.her.constants.AudioOffload
import com.fenrir.her.constants.AudioQualityKey
import com.fenrir.her.constants.AutoDownloadOnLikeKey
import com.fenrir.her.constants.AutoLoadMoreKey
import com.fenrir.her.constants.AutoSkipNextOnErrorKey
import com.fenrir.her.constants.AutomixCrossfadeKey
import com.fenrir.her.constants.CrossfadeDurationKey
import com.fenrir.her.constants.CrossfadeEnabledKey
import com.fenrir.her.constants.CrossfadeGaplessKey
import com.fenrir.her.constants.DisableLoadMoreWhenRepeatAllKey
import com.fenrir.her.constants.DiscordTokenKey
import com.fenrir.her.constants.EnableDiscordRPCKey
import com.fenrir.her.constants.EnableLastFMScrobblingKey
import com.fenrir.her.constants.HideExplicitKey
import com.fenrir.her.constants.HideVideoSongsKey
import com.fenrir.her.constants.HistoryDuration
import com.fenrir.her.constants.IpVersionKey
import com.fenrir.her.constants.LastFMSessionKey
import com.fenrir.her.constants.LastFMUseNowPlaying
import com.fenrir.her.constants.LastFMUseSendLikes
import com.fenrir.her.constants.MediaSessionConstants.CommandToggleLike
import com.fenrir.her.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.fenrir.her.constants.MediaSessionConstants.CommandToggleShuffle
import com.fenrir.her.constants.MediaSessionConstants.CommandToggleStartRadio
import com.fenrir.her.constants.PauseListenHistoryKey
import com.fenrir.her.constants.PauseOnMute
import com.fenrir.her.constants.PersistentQueueKey
import com.fenrir.her.constants.PersistentShuffleAcrossQueuesKey
import com.fenrir.her.constants.PlayerVolumeKey
import com.fenrir.her.constants.PreloadLyricsEnabledKey
import com.fenrir.her.constants.PreloadNextSongEnabledKey
import com.fenrir.her.constants.PreloadNextSongLimitKey
import com.fenrir.her.constants.PreventDuplicateTracksInQueueKey
import com.fenrir.her.constants.RememberShuffleAndRepeatKey
import com.fenrir.her.constants.RepeatModeKey
import com.fenrir.her.constants.ResumeOnBluetoothConnectKey
import com.fenrir.her.constants.ShowLyricsKey
import com.fenrir.her.constants.ShuffleModeKey
import com.fenrir.her.constants.ShufflePlaylistFirstKey
import com.fenrir.her.constants.SimilarContent
import com.fenrir.her.constants.SkipSilenceInstantKey
import com.fenrir.her.constants.SkipSilenceKey
import com.fenrir.her.constants.SpatialAudioKey
import com.fenrir.her.db.MusicDatabase
import com.fenrir.her.db.entities.BeatInfoEntity
import com.fenrir.her.db.entities.Event
import com.fenrir.her.db.entities.FormatEntity
import com.fenrir.her.db.entities.LyricsEntity
import com.fenrir.her.db.entities.RelatedSongMap
import com.fenrir.her.db.entities.Song
import com.fenrir.her.di.DownloadCache
import com.fenrir.her.di.PlayerCache
import com.fenrir.her.echomusic.updater.downloadmanager.EchoNotificationProvider
import com.fenrir.her.eq.EqualizerService
import com.fenrir.her.eq.audio.AutomixDuckAudioProcessor
import com.fenrir.her.eq.audio.CustomEqualizerAudioProcessor
import com.fenrir.her.eq.audio.StereoWidenerAudioProcessor
import com.fenrir.her.eq.data.EQProfileRepository
import com.fenrir.her.extensions.SilentHandler
import com.fenrir.her.extensions.collect
import com.fenrir.her.extensions.collectLatest
import com.fenrir.her.extensions.currentMetadata
import com.fenrir.her.extensions.findNextMediaItemById
import com.fenrir.her.extensions.mediaItems
import com.fenrir.her.extensions.metadata
import com.fenrir.her.extensions.setOffloadEnabled
import com.fenrir.her.extensions.toEnum
import com.fenrir.her.extensions.toMediaItem
import com.fenrir.her.lyrics.LyricsHelper
import com.fenrir.her.models.PersistPlayerState
import com.fenrir.her.models.PersistQueue
import com.fenrir.her.models.toMediaMetadata
import com.fenrir.her.playback.audio.BeatAnalyzer
import com.fenrir.her.playback.audio.SilenceDetectorAudioProcessor
import com.fenrir.her.playback.queues.EmptyQueue
import com.fenrir.her.playback.queues.Queue
import com.fenrir.her.playback.queues.YouTubeQueue
import com.fenrir.her.playback.queues.filterExplicit
import com.fenrir.her.playback.queues.filterVideoSongs
import com.fenrir.her.ui.screens.settings.DiscordPresenceManager
import com.fenrir.her.utils.CoilBitmapLoader
import com.fenrir.her.utils.NetworkConnectivityObserver
import com.fenrir.her.utils.ScrobbleManager
import com.fenrir.her.utils.YTPlayerUtils
import com.fenrir.her.utils.dataStore
import com.fenrir.her.utils.get
import com.fenrir.her.utils.isLocalMediaId
import com.fenrir.her.utils.reportException
import com.fenrir.her.widget.EchoMusicWidgetManager
import com.fenrir.her.widget.MusicWidgetReceiver
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService(), Player.Listener, PlaybackStatsListener.Callback {
  @Inject lateinit var database: MusicDatabase

  @Inject lateinit var lyricsHelper: com.fenrir.her.lyrics.LyricsHelper

  @Inject lateinit var syncUtils: com.fenrir.her.utils.SyncUtils

  @Inject lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

  @Inject lateinit var equalizerService: EqualizerService

  @Inject lateinit var eqProfileRepository: EQProfileRepository

  @Inject lateinit var widgetManager: com.fenrir.her.widget.EchoMusicWidgetManager

  @Inject
  lateinit var listenTogetherManager: com.fenrir.her.listentogether.ListenTogetherManager

  private lateinit var audioManager: AudioManager
  // Wi-Fi Lock: Prevents modern Wi-Fi 6/7 routers from putting the Wi-Fi chip into
  // low-power sleep mode while music is actively streaming in the background.
  // Without this, the router's power-saving protocol (Target Wake Time) causes
  // packet delays, leading to audio buffering or playback stopping after the screen turns off.
  private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
  private var audioFocusRequest: AudioFocusRequest? = null
  private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
  private var wasPlayingBeforeAudioFocusLoss = false
  private var hasAudioFocus = false
  private var reentrantFocusGain = false
  private var wasPlayingBeforeVolumeMute = false
  private var isPausedByVolumeMute = false
  var preferredDeviceId: Int? = null
    private set

  private var crossfadeEnabled = false
  private var crossfadeDuration = 5000f
  private var crossfadeGapless = true
  private var crossfadeTriggerJob: Job? = null

  private var automixEnabled = false
  private var activeAutomixPlan: AutomixPlan? = null

  private suspend fun computeAutomixPlan(
    baseTriggerTime: Long,
    trackDuration: Long
  ): AutomixPlanResult {
    val pair = currentAutomixPair() ?: return AutomixPlanResult(plan = null, pairAnalyzed = false)
    val currentId = pair.currentId
    val nextId = pair.nextId

    val (outBeat, inBeat) =
      withContext(Dispatchers.IO) { database.beatInfo(currentId) to database.beatInfo(nextId) }
    if (outBeat == null) maybeAnalyzeBeat(currentId, BeatAnalysisPriority.IMMEDIATE)
    if (inBeat == null && nextId != currentId)
      maybeAnalyzeBeat(nextId, BeatAnalysisPriority.IMMEDIATE)
    val partialDebug =
      AutomixDebugInfo(
        status = "",
        outBpm = outBeat?.bpm,
        outConfidence = outBeat?.confidence,
        outMixOutMs = outBeat?.mixOutPointMs,
        inBpm = inBeat?.bpm,
        inConfidence = inBeat?.confidence,
        inMixInMs = inBeat?.mixInPointMs,
      )
    if (outBeat == null || inBeat == null) {
      Timber.tag(TAG)
        .d(
          "Automix fallback: beat info missing (current=%s next=%s)",
          outBeat != null,
          inBeat != null
        )
      automixDebugInfo.value =
        partialDebug.copy(
          status =
            "fallback: analysis pending (" +
              (if (outBeat == null) "current" else "") +
              (if (outBeat == null && inBeat == null) "+" else "") +
              (if (inBeat == null) "next" else "") +
              ")"
        )
      return AutomixPlanResult(plan = null, pairAnalyzed = false)
    }
    if (
      outBeat.confidence < 0.3f || inBeat.confidence < 0.3f || outBeat.bpm <= 0f || inBeat.bpm <= 0f
    ) {
      Timber.tag(TAG)
        .d(
          "Automix fallback: low confidence (out=%.2f/%.0fbpm in=%.2f/%.0fbpm)",
          outBeat.confidence,
          outBeat.bpm,
          inBeat.confidence,
          inBeat.bpm
        )
      automixDebugInfo.value = partialDebug.copy(status = "fallback: low confidence")
      return AutomixPlanResult(plan = null, pairAnalyzed = true)
    }

    val periodMs = (60_000f / outBeat.bpm).toDouble()

    // DJ blend: 16 beats of the outgoing track (4 bars), 6-16s bounds.
    val overlapMs = (16 * periodMs).toLong().coerceIn(6_000L, 16_000L)

    // Dynamic mix-out: start the transition where the song's body ends (outro begins)
    // rather than a fixed distance from the end. Sentinel <= 0 means "no outro found".
    val latestTrigger = trackDuration - overlapMs
    val mixOut = outBeat.mixOutPointMs?.takeIf { it > 0 }
    val effectiveTrigger = mixOut?.coerceAtMost(latestTrigger) ?: latestTrigger

    // Snap the fade start onto an 8-beat phrase boundary of the outgoing track's grid.
    // Anchor past the current position so re-planning late (pause/seek near the end)
    // still lands on the next musical boundary instead of giving up.
    val phraseMs = periodMs * 8
    val anchor = maxOf(effectiveTrigger, player.currentPosition + 1000)
    val k = ((anchor - outBeat.firstBeatOffsetMs) / phraseMs).toLong()
    var triggerTime = (outBeat.firstBeatOffsetMs + k * phraseMs).toLong()
    if (triggerTime < anchor)
      triggerTime = (outBeat.firstBeatOffsetMs + (k + 1) * phraseMs).toLong()
    // Phrase-snapping can push triggerTime past latestTrigger by up to ~1 phrase.
    // The outgoing player keeps its own playlist and keeps advancing in real time
    // during the fade, so the full overlap must fit before its natural end or it
    // auto-advances on its own mid-fade — playing the next track a second time (or
    // wrapping to track 1 on repeat-all). Rather than discarding the whole plan for
    // a few seconds of overshoot, shrink the overlap to whatever room is actually
    // left; only fall back if that leaves too little room to blend at all.
    val roomMs = trackDuration - 500 - triggerTime
    val effectiveOverlapMs = overlapMs.coerceAtMost(roomMs)
    if (effectiveOverlapMs < 3000L || triggerTime >= trackDuration - 3000) {
      Timber.tag(TAG)
        .d(
          "Automix fallback: trigger %d out of range (pos=%d dur=%d overlap=%d)",
          triggerTime,
          player.currentPosition,
          trackDuration,
          overlapMs
        )
      automixDebugInfo.value = partialDebug.copy(status = "fallback: trigger out of range")
      return AutomixPlanResult(plan = null, pairAnalyzed = true)
    }

    // Fold octave errors, then cap pitch-preserving stretch at ±8%.
    var tempoRatio = outBeat.bpm / inBeat.bpm
    while (tempoRatio > 1.5f) tempoRatio /= 2f
    while (tempoRatio < 0.667f) tempoRatio *= 2f
    if (tempoRatio !in 0.92f..1.08f) tempoRatio = 1f

    // Harmonic correction: compare keys via their relative-major pitch class (a minor
    // key's relative major sits 3 semitones up), then pitch-shift the incoming track
    // the minimal circular distance to align. Skip when either key is unknown, when
    // they already match, or when the shift would be large enough to sound worse than
    // the clash it's fixing (>3 semitones).
    var pitchRatio = 1f
    val outKeyClass = outBeat.keyPitchClass
    val inKeyClass = inBeat.keyPitchClass
    if (outKeyClass != null && inKeyClass != null) {
      val outEffective = if (outBeat.keyIsMinor == true) (outKeyClass + 3) % 12 else outKeyClass
      val inEffective = if (inBeat.keyIsMinor == true) (inKeyClass + 3) % 12 else inKeyClass
      var semitoneShift = (outEffective - inEffective) % 12
      if (semitoneShift > 6) semitoneShift -= 12
      if (semitoneShift < -6) semitoneShift += 12
      if (semitoneShift != 0 && kotlin.math.abs(semitoneShift) <= 3) {
        pitchRatio = Math.pow(2.0, semitoneShift / 12.0).toFloat()
      }
    }

    // Dynamic mix-in: skip the incoming track's intro, snapped onto its own 8-beat grid.
    val inPeriodMs = (60_000f / inBeat.bpm).toDouble()
    val rawStart = inBeat.mixInPointMs?.takeIf { it > 0 } ?: inBeat.firstBeatOffsetMs
    val inPhraseMs = inPeriodMs * 8
    val inK =
      kotlin.math.ceil((rawStart - inBeat.firstBeatOffsetMs) / inPhraseMs).toLong().coerceAtLeast(0)
    val incomingStart = (inBeat.firstBeatOffsetMs + inK * inPhraseMs).toLong()

    val plan =
      AutomixPlan(
        currentId = currentId,
        nextId = nextId,
        triggerTimeMs = triggerTime,
        incomingStartMs = incomingStart,
        tempoRatio = tempoRatio,
        pitchRatio = pitchRatio,
        overlapMs = effectiveOverlapMs,
      )
    Timber.tag(TAG)
      .d(
        "Automix plan: trigger=%dms incomingStart=%dms tempoRatio=%.3f pitchRatio=%.3f overlap=%dms",
        plan.triggerTimeMs,
        plan.incomingStartMs,
        plan.tempoRatio,
        plan.pitchRatio,
        plan.overlapMs
      )
    automixDebugInfo.value =
      partialDebug.copy(
        status = "plan ready",
        triggerTimeMs = plan.triggerTimeMs,
        incomingStartMs = plan.incomingStartMs,
        tempoRatio = plan.tempoRatio,
      )
    return AutomixPlanResult(plan = plan, pairAnalyzed = true)
  }

  /**
   * Queue lookahead: analyze the next few upcoming tracks while the current one plays, so beat data
   * is ready by the time their transition is planned.
   */
  private fun analyzeUpcomingTracks() {
    val timeline = player.currentTimeline
    if (timeline.isEmpty) return
    // Skip the immediate next item: the transition planner already enqueues it
    // (with priority over this far-queue lookahead).
    var index =
      timeline.getNextWindowIndex(
        player.currentMediaItemIndex,
        REPEAT_MODE_OFF,
        player.shuffleModeEnabled
      )
    if (index == C.INDEX_UNSET) return
    repeat(2) {
      index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, player.shuffleModeEnabled)
      if (index == C.INDEX_UNSET) return
      maybeAnalyzeBeat(player.getMediaItemAt(index).mediaId, BeatAnalysisPriority.LOOKAHEAD)
    }
  }

  /**
   * Lazy per-track beat analysis; fetches audio through the playback data-source chain
   * (cache-first, network otherwise) and stores the result permanently. Serialized so lookahead
   * doesn't stack up parallel downloads.
   */
  private fun maybeAnalyzeBeat(
    mediaId: String,
    priority: BeatAnalysisPriority = BeatAnalysisPriority.IMMEDIATE,
  ) {
    synchronized(beatAnalysisJobs) {
      val existing = beatAnalysisJobs[mediaId]
      if (existing != null) {
        // A fetch is already running for this track. Never cancel-and-restart it:
        // that throws away the bytes already downloaded (often megabytes) right when
        // the track is about to be needed. Promote its priority in place instead so
        // the in-flight download finishes and its result is reused.
        if (
          priority == BeatAnalysisPriority.IMMEDIATE &&
            existing.priority == BeatAnalysisPriority.LOOKAHEAD
        ) {
          Timber.tag(TAG).d("Beat analysis priority promoted in place for %s", mediaId)
          beatAnalysisJobs[mediaId] = existing.copy(priority = BeatAnalysisPriority.IMMEDIATE)
        }
        return
      }

      val job =
        scope.launch(Dispatchers.IO) {
          val mutex =
            when (priority) {
              BeatAnalysisPriority.IMMEDIATE -> immediateBeatAnalysisMutex
              BeatAnalysisPriority.LOOKAHEAD -> lookaheadBeatAnalysisMutex
            }
          mutex.withLock {
            try {
              runBeatAnalysis(mediaId, priority)
            } catch (e: kotlinx.coroutines.CancellationException) {
              Timber.tag(TAG).d("Beat analysis cancelled for %s (%s)", mediaId, priority)
              throw e
            } catch (e: Exception) {
              Timber.tag(TAG).w(e, "Beat analysis failed for $mediaId")
            } finally {
              synchronized(beatAnalysisJobs) {
                val current = beatAnalysisJobs[mediaId]
                if (current?.job == coroutineContext[Job]) {
                  beatAnalysisJobs.remove(mediaId)
                }
              }
            }
          }
        }
      beatAnalysisJobs[mediaId] = BeatAnalysisHandle(priority, job)
    }
  }

  private suspend fun runBeatAnalysis(mediaId: String, priority: BeatAnalysisPriority) {
    val existing = database.beatInfo(mediaId)
    // Skip when analyzed with mix points (null mixOut = pre-mix-point row, rescan once).
    if (existing != null && !(existing.bpm > 0f && existing.mixOutPointMs == null)) return
    Timber.tag(TAG).d("Beat analysis starting for %s (%s)", mediaId, priority)

    val result: BeatAnalyzer.Result?
    val dataComplete: Boolean
    val startedAt = android.os.SystemClock.elapsedRealtime()
    val analysisContext = coroutineContext
    fun timedOutOrCancelled(): Boolean =
      !analysisContext.isActive ||
        android.os.SystemClock.elapsedRealtime() - startedAt > beatAnalysisTimeoutMs(priority)
    if (mediaId.isLocalMediaId()) {
      result =
        BeatAnalyzer.analyzeUri(
          this@MusicService,
          android.net.Uri.parse(mediaId),
          shouldCancel = ::timedOutOrCancelled,
        )
      dataComplete = true
    } else {
      val fetched =
        BeatAnalyzer.analyzeStream(
          analysisDataSourceFactory,
          mediaId,
          cacheDir,
          shouldCancel = { !analysisContext.isActive || timedOutOrCancelled() },
        )
          ?: run {
            Timber.tag(TAG).d("Beat analysis skipped for %s: fetch failed", mediaId)
            return // retry on a later transition
          }
      result = fetched.result
      dataComplete = fetched.complete
    }
    Timber.tag(TAG)
      .d(
        "Beat analysis done for %s: %s",
        mediaId,
        result?.let {
          "bpm=%.1f conf=%.2f mixIn=%s mixOut=%s"
            .format(it.bpm, it.confidence, it.mixInPointMs, it.mixOutPointMs)
        } ?: "failed (complete=$dataComplete)"
      )
    if (result == null && !dataComplete) return // partial data; retry when fully cached

    val entity =
      result?.let {
        BeatInfoEntity(
          mediaId,
          it.bpm,
          it.firstBeatOffsetMs,
          it.confidence,
          mixInPointMs = it.mixInPointMs ?: -1L, // -1 sentinel: scanned, none found
          mixOutPointMs = it.mixOutPointMs ?: -1L,
          keyPitchClass = it.keyPitchClass,
          keyIsMinor = it.keyIsMinor,
        )
      } ?: BeatInfoEntity(mediaId, 0f, 0L, 0f, mixInPointMs = -1L, mixOutPointMs = -1L)
    withContext(Dispatchers.IO) { database.upsert(entity) }

    // Fresh data may unlock a beat-aligned plan for the ongoing transition:
    // re-arm the scheduler if this track is the current or next item.
    withContext(Dispatchers.Main) {
      val currentId = player.currentMediaItem?.mediaId
      val nextIndex = player.nextMediaItemIndex
      val nextId =
        if (nextIndex != C.INDEX_UNSET) player.getMediaItemAt(nextIndex).mediaId else null
      if (mediaId == currentId || mediaId == nextId) scheduleCrossfade()
    }
  }

  private fun isNextItemGapless(): Boolean {
    val currentMediaItem = player.currentMediaItem ?: return false
    if (currentMediaItem.mediaId.isLocalMediaId()) {
      return false // Allow crossfade/automix for local media
    }
    val current = currentMediaItem.mediaMetadata
    val nextIndex = player.nextMediaItemIndex
    if (nextIndex == C.INDEX_UNSET) return false
    val next = player.getMediaItemAt(nextIndex).mediaMetadata
    return current.albumTitle != null && current.albumTitle == next.albumTitle
  }

  private fun releasePrebuffered() {
    val pb = prebuffered ?: return
    prebuffered = null
    playerDuckProcessors.remove(pb.player)
    playerSilenceProcessors.remove(pb.player)
    playerStereoWideners.remove(pb.player)
    try {
      pb.player.removeListener(secondaryPlayerListener)
      pb.player.stop()
      pb.player.clearMediaItems()
      pb.player.release()
    } catch (e: Exception) {
      Timber.tag(TAG).d(e, "Failed to release prebuffered crossfade player")
    }
  }

  /**
   * Builds and prepares the secondary player ahead of the actual trigger, muted and not yet
   * playing, so the blend doesn't have to cold-start a fresh decode/buffer right when it needs to
   * be audible. Adopted by [startCrossfade] if it's still valid by then.
   */
  private fun prebufferSecondaryPlayer(plan: AutomixPlan?) {
    if (isCrossfading.value || secondaryPlayer != null || prebuffered != null) return

    val savedRepeatMode = cachedRepeatMode
    val savedShuffleEnabled = cachedShuffleEnabled
    val targetIndex =
      if (savedRepeatMode == REPEAT_MODE_ONE) {
        player.currentMediaItemIndex
      } else {
        player.nextMediaItemIndex
      }
    if (targetIndex == C.INDEX_UNSET) return
    val targetMediaId = player.getMediaItemAt(targetIndex).mediaId

    val secPlayer = createExoPlayer()
    secPlayer.addListener(secondaryPlayerListener)

    val itemCount = player.mediaItemCount
    val items = mutableListOf<MediaItem>()
    for (i in 0 until itemCount) items.add(player.getMediaItemAt(i))
    secPlayer.setMediaItems(items)

    secPlayer.seekTo(targetIndex, plan?.incomingStartMs ?: 0)
    if (plan != null) {
      val base =
        try {
          player.playbackParameters
        } catch (e: Exception) {
          PlaybackParameters.DEFAULT
        }
      if (base != PlaybackParameters.DEFAULT) secPlayer.playbackParameters = base
    }
    secPlayer.volume = 0f
    secPlayer.repeatMode = savedRepeatMode
    secPlayer.shuffleModeEnabled = savedShuffleEnabled
    secPlayer.prepare() // playWhenReady left false: buffers ahead without playing.

    prebuffered = PrebufferedTransition(secPlayer, plan, targetMediaId)
  }

  private fun startCrossfade(plan: AutomixPlan? = null) {
    if (isCrossfading.value) return

    val savedRepeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
    val savedShuffleEnabled = runBlocking { dataStore.get(ShuffleModeKey, false) }

    val targetIndex =
      if (savedRepeatMode == REPEAT_MODE_ONE) {
        player.currentMediaItemIndex
      } else {
        player.nextMediaItemIndex
      }
    if (targetIndex == C.INDEX_UNSET) return
    val targetMediaId = player.getMediaItemAt(targetIndex).mediaId

    activeAutomixPlan = plan

    val pb = prebuffered
    val secPlayer: ExoPlayer
    if (pb != null && pb.targetMediaId == targetMediaId) {
      // Already buffered ahead of time — adopt it instead of cold-starting a new one.
      secPlayer = pb.player
      activeAutomixPlan = pb.plan
      prebuffered = null
    } else {
      releasePrebuffered() // stale — buffered for a track that's no longer next.

      secPlayer = createExoPlayer()
      secPlayer.addListener(secondaryPlayerListener)

      val itemCount = player.mediaItemCount
      val items = mutableListOf<MediaItem>()
      for (i in 0 until itemCount) items.add(player.getMediaItemAt(i))
      secPlayer.setMediaItems(items)

      // Beat-aligned: start the incoming track on its first downbeat.
      secPlayer.seekTo(targetIndex, plan?.incomingStartMs ?: 0)
      if (plan != null) {
        val base =
          try {
            player.playbackParameters
          } catch (e: Exception) {
            PlaybackParameters.DEFAULT
          }
        if (base != PlaybackParameters.DEFAULT) secPlayer.playbackParameters = base
      }
      secPlayer.volume = 0f
      secPlayer.repeatMode = savedRepeatMode
      secPlayer.shuffleModeEnabled = savedShuffleEnabled
      secPlayer.prepare()
    }

    secondaryPlayer = secPlayer
    secPlayer.playWhenReady = true

    performCrossfadeSwap()

    if (savedShuffleEnabled) {
      val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
      applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
    }
  }

  private fun performCrossfadeSwap() {
    isCrossfading.value = true
    isAutomixing.value = activeAutomixPlan != null
    if (activeAutomixPlan != null) {
      automixDebugInfo.value = automixDebugInfo.value?.copy(status = "automixing now")
    }
    val nextPlayer = secondaryPlayer ?: return
    val currentPlayer = player

    fadingPlayer = currentPlayer
    player = nextPlayer
    _playerFlow.value = player
    secondaryPlayer = null

    // The outgoing player keeps its full playlist and keeps advancing in real time
    // while it fades out. If it reaches its own natural end before cleanupCrossfade
    // stops it (trigger-time math off, or the fade loop lagging behind due to a
    // scheduling hiccup), it would auto-advance on its own — playing the next track
    // a second time, or wrapping to track 1 on repeat-all. Truncate its playlist so
    // it has nowhere to advance to; worst case it just stops.
    try {
      val idx = currentPlayer.currentMediaItemIndex
      if (idx != C.INDEX_UNSET && idx + 1 < currentPlayer.mediaItemCount) {
        currentPlayer.removeMediaItems(idx + 1, currentPlayer.mediaItemCount)
      }
      currentPlayer.repeatMode = REPEAT_MODE_OFF
    } catch (e: Exception) {
      Timber.tag(TAG).d(e, "Failed to truncate fading player's playlist")
    }

    fadingPlayer?.removeListener(this)
    fadingPlayer?.removeListener(sleepTimer)

    player.addListener(
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isCrossfading.value && fadingPlayer != null) {
            try {
              if (isPlaying) {
                fadingPlayer?.play()
              } else if (
                !player.playWhenReady ||
                  player.playbackSuppressionReason !=
                    androidx.media3.common.Player.PLAYBACK_SUPPRESSION_REASON_NONE
              ) {
                fadingPlayer?.pause()
              }
            } catch (e: Exception) {
              Timber.tag(TAG).e(e, "Error syncing fadingPlayer play state")
            }
          } else {
            player.removeListener(this)
          }
        }
      }
    )

    nextPlayer.removeListener(secondaryPlayerListener)
    nextPlayer.addListener(this)
    nextPlayer.addListener(sleepTimer)

    sleepTimer.player = player

    try {
      (mediaSession as MediaSession).player = player
    } catch (e: Exception) {
      timber.log.Timber.e(e, "Failed to swap player in MediaSession")
    }

    // The crossfade swap moves playback to a brand-new ExoPlayer with its own
    // audio session id, but this player's listener was attached after the
    // seek/prepare already happened, so no EVENT_MEDIA_ITEM_TRANSITION fires for
    // it. Without this, the LoudnessEnhancer and system-EQ session stay bound to
    // the outgoing (soon-to-be-released) session, so the incoming track plays
    // without normalization/EQ.
    currentMediaMetadata.value = player.currentMetadata
    val oldSessionId = fadingPlayer?.audioSessionId
    // Keep the current enhancer (still bound to the outgoing session) alive and attached
    // through the fade instead of releasing it, so the outgoing track stays normalized
    // while it fades out. A fresh enhancer for the incoming session is created below.
    // cleanupCrossfade releases this once the fade is done.
    try {
      fadingLoudnessEnhancer?.release()
    } catch (e: Exception) {
      Timber.tag(TAG).d(e, "Failed releasing stale fading enhancer")
    }
    fadingLoudnessEnhancer = loudnessEnhancer
    loudnessEnhancer = null
    if (isAudioEffectSessionOpened) {
      if (oldSessionId != null && oldSessionId != C.AUDIO_SESSION_ID_UNSET && oldSessionId > 0) {
        sendBroadcast(
          Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, oldSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
          },
        )
      }
      isAudioEffectSessionOpened = false
      openAudioEffectSession()
    } else {
      setupLoudnessEnhancer()
    }

    crossfadeJob =
      scope.launch {
        val djPlan = activeAutomixPlan
        val duration = djPlan?.overlapMs ?: crossfadeDuration.toLong()
        // Fine-grained ramp: aim for ~15ms per volume step so each gain increment is
        // below the threshold of audibility. Coarse steps (the old 100ms) make the fade
        // a stepped "zipper"/click; at 15ms the ramp sounds continuous. Volume writes are
        // near-free, so the extra steps cost nothing meaningful.
        val steps = (duration / 15L).toInt().coerceIn(50, 800)
        val stepTime = duration / steps
        val startVolume =
          try {
            fadingPlayer?.volume ?: 1f
          } catch (e: Exception) {
            1f
          }

        // Bass-swap ducking (DJ blend only): cut the outgoing track's low end as it
        // drops and hold the incoming track's low end back until it takes over, so
        // two full basslines don't sum into mud during the overlap.
        val outDuck = fadingPlayer?.let { playerDuckProcessors[it] }
        val inDuck = playerDuckProcessors[player]

        // Equal-power curve: sin/cos gains keep combined signal energy ~constant
        // through the blend, so linearly summing two tracks doesn't dip in
        // perceived loudness at the midpoint the way linear/smoothstep gain does.
        fun equalPowerIn(edge0: Float, edge1: Float, x: Float): Float {
          val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
          return kotlin.math.sin(t * (Math.PI / 2.0).toFloat())
        }
        fun equalPowerOut(edge0: Float, edge1: Float, x: Float): Float {
          val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
          return kotlin.math.cos(t * (Math.PI / 2.0).toFloat())
        }

        try {
          for (i in 0..steps) {
            if (!isActive) break

            while (!player.isPlaying && isActive) {
              delay(100)
            }

            if (
              fadingPlayer?.playbackState == androidx.media3.common.Player.STATE_ENDED ||
                fadingPlayer?.playbackState == androidx.media3.common.Player.STATE_IDLE
            ) {
              player.volume = startVolume
              break
            }

            val progress = i / steps.toFloat()
            // Fade-out then fade-in with a gentle dip: the outgoing track drops away
            // over the first ~60% of the blend, the incoming rises over the last ~60%,
            // so they overlap only through the middle where both sit well below full.
            // Old track leaves, new one arrives — no sudden level match, no boost.
            // Both curves are cosine/sine eased, so the ramp stays click-free.
            val fadeOut = equalPowerOut(0f, 0.6f, progress)
            val fadeIn = equalPowerIn(0.4f, 1f, progress)

            try {
              player.volume = startVolume * fadeIn
              fadingPlayer?.volume = startVolume * fadeOut
            } catch (e: Exception) {
              break
            }

            if (djPlan != null) {
              // Outgoing bass cuts through the same 0.45-1.0 window it fades
              // out in; incoming bass fills back in through 0-0.55.
              outDuck?.setMix(equalPowerIn(0.45f, 1f, progress))
              inDuck?.setMix(1f - equalPowerIn(0f, 0.55f, progress))
            }

            delay(stepTime)
          }
        } finally {
          try {
            fadingPlayer?.volume = 0f
            player.volume = startVolume
          } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Crossfade volume reset skipped, player likely released")
          }
          outDuck?.resetGain()
          inDuck?.resetGain()
          cleanupCrossfade()
          activeAutomixPlan = null
        }
      }
  }

  private fun cleanupCrossfade() {
    try {
      fadingLoudnessEnhancer?.release()
    } catch (e: Exception) {
      Timber.tag(TAG).d(e, "Failed releasing fading enhancer")
    } finally {
      fadingLoudnessEnhancer = null
    }
    fadingPlayer?.let { playerDuckProcessors.remove(it) }
    fadingPlayer?.let { playerStereoWideners.remove(it) }
    fadingPlayer?.stop()
    fadingPlayer?.clearMediaItems()
    fadingPlayer?.release()
    fadingPlayer = null
    isCrossfading.value = false
    isAutomixing.value = false
    sleepTimer.notifySongTransition()
  }

  companion object {
    const val ROOT = "root"
    const val SONG = "song"
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val PLAYLIST = "playlist"
    const val YOUTUBE_PLAYLIST = "youtube_playlist"
    const val SEARCH = "search"
    const val SHUFFLE_ACTION = "__shuffle__"

    const val CHANNEL_ID = "music_channel_01"
    const val NOTIFICATION_ID = 888
    const val ERROR_CODE_NO_STREAM = 1000001
    const val CHUNK_LENGTH = 512 * 1024L
    const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
    const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
    /** How far ahead of the crossfade trigger to start buffering the incoming track. */
    const val PREBUFFER_LEAD_MS = 10000L
    const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
    const val MAX_CONSECUTIVE_ERR = 5
    const val MAX_RETRY_COUNT = 10

    // Wide enough for AGGRESSIVE preset (+700mB) stacked on top of positive
    // loudness-normalization gain, without letting the two combine into distortion.
    private const val MAX_GAIN_MB = 1000
    private const val MIN_GAIN_MB = -1500

    /** Fixed side-channel boost applied when the spatial-audio toggle is on. */
    private const val SPATIAL_AUDIO_WIDTH = 1.4f

    private const val TAG = "MusicService"

    @Volatile
    var isRunning = false
      private set
  }

  private var preloadJob: kotlinx.coroutines.Job? = null

  private fun preloadUpcomingItems() {
    val preloadEnabled = cachedPreloadEnabled
    if (!preloadEnabled) return

    val preloadLimit = cachedPreloadLimit
    val preloadLyrics = cachedPreloadLyrics

    val currentIndex = player.currentMediaItemIndex
    if (currentIndex == androidx.media3.common.C.INDEX_UNSET) return

    val limit = kotlin.math.min(preloadLimit, player.mediaItemCount - currentIndex - 1)
    if (limit <= 0) return

    val upcomingMediaIds = mutableListOf<String>()
    for (i in 1..limit) {
      upcomingMediaIds.add(player.getMediaItemAt(currentIndex + i).mediaId)
    }

    preloadJob?.cancel()
    preloadJob =
      scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        kotlinx.coroutines.delay(8000L) // 8-second grace period before prefetching
        for (mediaId in upcomingMediaIds) {

          val isFullyDownloaded = downloadCache.getCachedSpans(mediaId).isNotEmpty()
          if (
            !mediaId.isLocalMediaId() &&
              !songUrlCache.containsKey("${mediaId}_${audioQuality.name}") &&
              !isFullyDownloaded
          ) {
            Timber.tag(TAG).d("Preloading stream for $mediaId")
            kotlin.runCatching {
              val dbSong = database.song(mediaId).firstOrNull()
              val knownArtist =
                dbSong
                  ?.artists
                  ?.joinToString(separator = ", ") { artist -> artist.name }
                  ?.replace(" - Topic", "")

              val playbackData =
                com.fenrir.her.utils.YTPlayerUtils.playerResponseForPlayback(
                  videoId = mediaId,
                  audioQuality = audioQuality,
                  connectivityManager = connectivityManager
                )

              playbackData.getOrNull()?.streamUrl?.let { streamUrl ->
                songUrlCache["${mediaId}_${audioQuality.name}"] =
                  Pair(streamUrl, System.currentTimeMillis() + 1000 * 60 * 60)
                Timber.tag(TAG).d("Preloaded stream for $mediaId")
                
                kotlin.runCatching {
                  Timber.tag(TAG).d("AOT Preloading bytes for $mediaId")
                  val dataSpec = androidx.media3.datasource.DataSpec.Builder()
                    .setUri(android.net.Uri.parse(streamUrl))
                    .setKey("${mediaId}_${audioQuality.name}")
                    .build()
                  val cacheDataSource = createCacheDataSource().createDataSource()
                  val cacheWriter = androidx.media3.datasource.cache.CacheWriter(
                    cacheDataSource,
                    dataSpec,
                    null,
                    null
                  )
                  cacheWriter.cache()
                  Timber.tag(TAG).d("AOT Preloading bytes for $mediaId completed")
                }.onFailure { e ->
                  if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.tag(TAG).e(e, "AOT Preloading bytes failed for $mediaId")
                  }
                }
              }
            }
          }

          if (preloadLyrics) {
            val dbLyrics = database.lyrics(mediaId).firstOrNull()
            if (dbLyrics == null) {
              Timber.tag(TAG).d("Preloading lyrics for $mediaId")
              val dbSong = database.song(mediaId).firstOrNull()
              if (dbSong != null) {
                kotlin.runCatching {
                  val metadata =
                    com.fenrir.her.models.MediaMetadata(
                      id = dbSong.song.id,
                      title = dbSong.song.title,
                      artists =
                        dbSong.artists.map { artist ->
                          com.fenrir.her.models.MediaMetadata.Artist(artist.id, artist.name)
                        },
                      duration = dbSong.song.duration,
                      thumbnailUrl = dbSong.thumbnailUrl
                    )
                  val lyricsResult = lyricsHelper.getLyrics(metadata)
                  database.query {
                    upsert(
                      com.fenrir.her.db.entities.LyricsEntity(
                        id = mediaId,
                        lyrics = lyricsResult.lyrics ?: ""
                      )
                    )
                  }
                  Timber.tag(TAG).d("Preloaded lyrics for $mediaId")
                }
              }
            }
          }
        }
      }
  }

  private fun checkAndSubmitListenBrainzFinished() {
    listenBrainzCurrentMediaId?.let { mediaId ->
      val startTs = listenBrainzCurrentStartTs
      if (startTs > 0) {
        scope.launch {
          val mediaMetadata = player.mediaItems.find { it.mediaId == mediaId }?.metadata
          val dbSong = if (mediaMetadata == null) database.song(mediaId).firstOrNull() else null

          val title = mediaMetadata?.title ?: dbSong?.song?.title ?: return@launch
          val artistNames =
            mediaMetadata?.artists?.joinToString(" & ") { it.name }
              ?: dbSong?.artists?.joinToString(" & ") { it.name }
              ?: ""
          val releaseName = mediaMetadata?.album?.title ?: dbSong?.album?.title ?: ""
          val durationMs =
            mediaMetadata?.duration?.takeIf { it != -1 }?.times(1000L)
              ?: dbSong?.song?.duration?.takeIf { it != -1 }?.times(1000L)
              ?: 0L

          updateListenBrainz(
            title,
            artistNames,
            releaseName,
            durationMs,
            isFinished = true,
            startMs = startTs,
            endMs = System.currentTimeMillis()
          )
        }
      }
    }
    listenBrainzCurrentStartTs = 0L
    listenBrainzCurrentMediaId = null
  }

  private fun checkAndSubmitListenBrainzPlayingNow(mediaId: String) {
    scope.launch {
      val mediaMetadata = player.mediaItems.find { it.mediaId == mediaId }?.metadata
      val dbSong = if (mediaMetadata == null) database.song(mediaId).firstOrNull() else null

      val title = mediaMetadata?.title ?: dbSong?.song?.title ?: return@launch
      val artistNames =
        mediaMetadata?.artists?.joinToString(" & ") { it.name }
          ?: dbSong?.artists?.joinToString(" & ") { it.name }
          ?: ""
      val releaseName = mediaMetadata?.album?.title ?: dbSong?.album?.title ?: ""
      val durationMs =
        mediaMetadata?.duration?.takeIf { it != -1 }?.times(1000L)
          ?: dbSong?.song?.duration?.takeIf { it != -1 }?.times(1000L)
          ?: 0L

      updateListenBrainz(title, artistNames, releaseName, durationMs, isFinished = false)
    }
  }
}
