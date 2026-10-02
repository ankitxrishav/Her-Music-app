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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

object CouplePlaybackSyncManager {
  private var syncJob: Job? = null
  private var broadcastJob: Job? = null
  private var attachedPlayer: Player? = null
  private var playerListener: Player.Listener? = null

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
    stopBroadcaster()
    if (spaceId.isBlank()) return

    fun sendBroadcast() {
      scope.launch(Dispatchers.Main) {
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

    broadcastJob = scope.launch(Dispatchers.Main) {
      while (isActive) {
        try {
          val p = playerConnection?.player
          val m = playerConnection?.service?.currentMediaMetadata?.value
          if (p != null && m != null) {
            val live = CoupleLivePlayback(
              songId = m.id,
              title = m.title,
              artist = m.artists.joinToString { it.name },
              thumbnailUrl = m.thumbnailUrl,
              isPlaying = p.isPlaying,
              positionMs = p.currentPosition.coerceAtLeast(0L),
              durationMs = p.duration.coerceAtLeast(0L),
              timestampEpochMs = System.currentTimeMillis(),
              senderRole = role.name,
              senderName = myName
            )
            withContext(Dispatchers.IO) {
              FirebaseCoupleSync.broadcastLivePlayback(spaceId, live, customUrl)
            }
          }
        } catch (_: Exception) {}
        delay(1200)
      }
    }
  }

  fun stopBroadcaster() {
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
    stopLiveSyncFollow()
    if (spaceId.isBlank()) return

    val partnerRole = if (myRole == CoupleAuthGuard.CoupleRole.HER) {
      CoupleAuthGuard.CoupleRole.HIM
    } else {
      CoupleAuthGuard.CoupleRole.HER
    }

    _isLiveSyncing.value = true

    syncJob = scope.launch(Dispatchers.Main) {
      while (isActive) {
        try {
          val partnerLive = withContext(Dispatchers.IO) {
            FirebaseCoupleSync.fetchPartnerLivePlayback(spaceId, partnerRole.name, customUrl)
          }

          if (partnerLive != null && partnerLive.senderRole != myRole.name && partnerLive.songId.isNotBlank()) {
            _partnerPlayback.value = partnerLive

            val player = playerConnection?.player
            val currentMeta = playerConnection?.service?.currentMediaMetadata?.value

            if (currentMeta?.id != partnerLive.songId) {
              playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = partnerLive.songId)))
              delay(1000)
            }

            val elapsed = if (partnerLive.isPlaying) {
              (System.currentTimeMillis() - partnerLive.timestampEpochMs).coerceAtLeast(0L)
            } else 0L
            val targetMs = partnerLive.positionMs + elapsed
            val currentPos = player?.currentPosition ?: 0L
            val drift = abs(currentPos - targetMs)

            if (drift > 1800L && targetMs >= 0L) {
              player?.seekTo(targetMs)
            }

            if (partnerLive.isPlaying && player?.isPlaying == false) {
              player.play()
            } else if (!partnerLive.isPlaying && player?.isPlaying == true) {
              player.pause()
              if (drift > 1800L && targetMs >= 0L) {
                player.seekTo(targetMs)
              }
            }
          }
        } catch (_: Exception) {}
        delay(1100)
      }
    }
  }

  fun stopLiveSyncFollow() {
    syncJob?.cancel()
    syncJob = null
    _isLiveSyncing.value = false
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
