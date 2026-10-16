package com.fenrir.her.couple

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.innertube.models.WatchEndpoint
import com.fenrir.her.auth.CoupleAuthGuard
import com.fenrir.her.playback.PlayerConnection
import com.fenrir.her.playback.queues.YouTubeQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

object CouplePlaybackSyncManager {
  private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var syncJob: Job? = null
  private var broadcastJob: Job? = null
  private var attachedPlayer: Player? = null
  private var playerListener: Player.Listener? = null

  private var lastRequestedSongId: String? = null
  private var lastRequestedAt = 0L

  private val _partnerPlayback = MutableStateFlow<CoupleLivePlayback?>(null)
  val partnerPlayback: StateFlow<CoupleLivePlayback?> = _partnerPlayback.asStateFlow()

  private val _isLiveSyncing = MutableStateFlow(false)
  val isLiveSyncing: StateFlow<Boolean> = _isLiveSyncing.asStateFlow()

  fun startBroadcaster(
    scope: CoroutineScope,
    spaceId: String,
    role: CoupleAuthGuard.CoupleRole,
    myName: String,
    playerConnection: PlayerConnection?,
    customUrl: String? = null
  ) {
    stopLiveSyncFollow()
    stopBroadcaster()
    if (spaceId.isBlank()) return

    fun sendBroadcast() {
      if (_isLiveSyncing.value) return
      managerScope.launch(Dispatchers.Main) {
        try {
          val player = playerConnection?.player ?: return@launch
          val meta = playerConnection.service.currentMediaMetadata.value ?: return@launch
          val live = CoupleLivePlayback(
            songId = meta.id,
            title = meta.title,
            artist = meta.artists.joinToString { it.name },
            thumbnailUrl = meta.thumbnailUrl,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            timestampEpochMs = System.currentTimeMillis(),
            senderRole = role.name,
            senderName = myName
          )
          withContext(Dispatchers.IO) {
            FirebaseCoupleSync.broadcastLivePlayback(spaceId, live, customUrl)
          }
        } catch (_: Exception) {}
      }
    }

    val player = playerConnection?.player
    if (player != null) {
      val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          sendBroadcast()
        }

        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int
        ) {
          sendBroadcast()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
          sendBroadcast()
        }
      }
      player.addListener(listener)
      attachedPlayer = player
      playerListener = listener
    }

    broadcastJob = managerScope.launch(Dispatchers.IO) {
      while (isActive) {
        try {
          if (!_isLiveSyncing.value) {
            val p = playerConnection?.player
            val m = playerConnection?.service?.currentMediaMetadata?.value
            if (p != null && m != null && p.isPlaying) {
              val live = CoupleLivePlayback(
                songId = m.id,
                title = m.title,
                artist = m.artists.joinToString { it.name },
                thumbnailUrl = m.thumbnailUrl,
                isPlaying = true,
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = p.duration.coerceAtLeast(0L),
                timestampEpochMs = System.currentTimeMillis(),
                senderRole = role.name,
                senderName = myName
              )
              FirebaseCoupleSync.broadcastLivePlayback(spaceId, live, customUrl)
            }
          }
        } catch (_: Exception) {}
        delay(2500)
      }
    }
  }

  fun stopBroadcaster(
    spaceId: String = "",
    role: CoupleAuthGuard.CoupleRole? = null,
    myName: String = "",
    playerConnection: PlayerConnection? = null,
    customUrl: String? = null
  ) {
    if (spaceId.isNotBlank() && role != null) {
      managerScope.launch(Dispatchers.IO) {
        try {
          val player = playerConnection?.player
          val meta = playerConnection?.service?.currentMediaMetadata?.value
          val live = CoupleLivePlayback(
            songId = meta?.id ?: "",
            title = meta?.title ?: "",
            artist = meta?.artists?.joinToString { it.name } ?: "",
            thumbnailUrl = meta?.thumbnailUrl,
            isPlaying = false,
            positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            durationMs = player?.duration?.coerceAtLeast(0L) ?: 0L,
            timestampEpochMs = System.currentTimeMillis(),
            senderRole = role.name,
            senderName = myName
          )
          FirebaseCoupleSync.broadcastLivePlayback(spaceId, live, customUrl)
        } catch (_: Exception) {}
      }
    }
    broadcastJob?.cancel()
    broadcastJob = null
    playerListener?.let { attachedPlayer?.removeListener(it) }
    playerListener = null
    attachedPlayer = null
  }

  fun startLiveSyncFollow(
    scope: CoroutineScope,
    spaceId: String,
    myRole: CoupleAuthGuard.CoupleRole,
    playerConnection: PlayerConnection?,
    customUrl: String? = null
  ) {
    stopBroadcaster()
    stopLiveSyncFollow()
    if (spaceId.isBlank()) return

    val partnerRole = if (myRole == CoupleAuthGuard.CoupleRole.HER) {
      CoupleAuthGuard.CoupleRole.HIM
    } else {
      CoupleAuthGuard.CoupleRole.HER
    }

    _isLiveSyncing.value = true

    syncJob = managerScope.launch {
      launch {
        FirebaseCoupleSync.liveFlow.collect { partnerLive ->
          if (partnerLive != null && partnerLive.senderRole != myRole.name && partnerLive.songId.isNotBlank()) {
            applyPartnerPlayback(partnerLive, playerConnection)
          }
        }
      }

      while (isActive) {
        try {
          val partnerLive = withContext(Dispatchers.IO) {
            FirebaseCoupleSync.fetchPartnerLivePlayback(spaceId, partnerRole.name, customUrl)
          }
          if (partnerLive != null && partnerLive.senderRole != myRole.name && partnerLive.songId.isNotBlank()) {
            applyPartnerPlayback(partnerLive, playerConnection)
          }
        } catch (_: Exception) {}
        delay(2500)
      }
    }

    syncJob?.invokeOnCompletion {
      _isLiveSyncing.value = false
    }
  }

  private fun applyPartnerPlayback(partnerLive: CoupleLivePlayback, playerConnection: PlayerConnection?) {
    _partnerPlayback.value = partnerLive
    val player = playerConnection?.player ?: return
    val currentMeta = playerConnection.service.currentMediaMetadata.value

    if (currentMeta?.id != partnerLive.songId) {
      val now = System.currentTimeMillis()
      if (lastRequestedSongId != partnerLive.songId || now - lastRequestedAt > 10000L) {
        lastRequestedSongId = partnerLive.songId
        lastRequestedAt = now
        playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = partnerLive.songId)))
      }
      return
    }

    val ageMs = (System.currentTimeMillis() - partnerLive.timestampEpochMs).coerceAtLeast(0L)
    val isFresh = ageMs < 25000L
    val isPlaying = partnerLive.isPlaying && isFresh
    val dur = partnerLive.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
    val elapsed = if (isPlaying) ageMs else 0L
    val targetMs = (partnerLive.positionMs + elapsed).coerceIn(0L, dur)
    val currentPos = player.currentPosition.coerceAtLeast(0L)
    val drift = abs(currentPos - targetMs)

    if (drift > 1200L && targetMs >= 0L) {
      player.seekTo(targetMs)
    }

    if (isPlaying && !player.isPlaying) {
      player.play()
    } else if (!isPlaying && player.isPlaying) {
      player.pause()
      if (drift > 1200L && targetMs >= 0L) {
        player.seekTo(targetMs)
      }
    }
  }

  fun stopLiveSyncFollow() {
    syncJob?.cancel()
    syncJob = null
    _isLiveSyncing.value = false
    lastRequestedSongId = null
    lastRequestedAt = 0L
  }

  fun disconnectSync(
    spaceId: String = "",
    role: CoupleAuthGuard.CoupleRole? = null,
    myName: String = "",
    playerConnection: PlayerConnection? = null,
    customUrl: String? = null
  ) {
    stopLiveSyncFollow()
    stopBroadcaster(spaceId, role, myName, playerConnection, customUrl)
  }

  fun fetchPartnerPreview(
    scope: CoroutineScope,
    spaceId: String,
    myRole: CoupleAuthGuard.CoupleRole,
    customUrl: String? = null
  ) {
    if (spaceId.isBlank()) return
    val partnerRole = if (myRole == CoupleAuthGuard.CoupleRole.HER) {
      CoupleAuthGuard.CoupleRole.HIM
    } else {
      CoupleAuthGuard.CoupleRole.HER
    }
    scope.launch(Dispatchers.IO) {
      val live = FirebaseCoupleSync.fetchPartnerLivePlayback(spaceId, partnerRole.name, customUrl)
      if (live != null) {
        withContext(Dispatchers.Main) {
          _partnerPlayback.value = live
        }
      }
    }
  }

  fun playPartnerSongNow(playerConnection: PlayerConnection?, songId: String) {
    if (songId.isNotBlank()) {
      playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = songId)))
    }
  }
}
