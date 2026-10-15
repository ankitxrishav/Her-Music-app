package com.fenrir.her.couple

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fenrir.her.R
import com.fenrir.her.auth.CoupleAuthGuard
import com.fenrir.her.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun formatTime(ms: Long): String {
  val s = (ms / 1000).coerceAtLeast(0L)
  val m = s / 60
  val rem = s % 60
  return "%02d:%02d".format(m, rem)
}

@Composable
fun CouplePlayTogetherCard(
  partnerName: String,
  role: CoupleAuthGuard.CoupleRole,
  spaceId: String,
  customFirebaseUrl: String?,
  playerConnection: PlayerConnection?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val partnerPlayback by CouplePlaybackSyncManager.partnerPlayback.collectAsState()
  val isLiveSyncing by CouplePlaybackSyncManager.isLiveSyncing.collectAsState()

  LaunchedEffect(spaceId, isLiveSyncing) {
    if (!isLiveSyncing && spaceId.isNotBlank()) {
      while (true) {
        CouplePlaybackSyncManager.fetchPartnerPreview(scope, spaceId, role, customFirebaseUrl)
        delay(3000)
      }
    }
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = Color(0xFFFF4081),
            modifier = Modifier.size(20.dp)
          )
          Column {
            Text(
              text = "Play Together 💕",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = if (role == CoupleAuthGuard.CoupleRole.HER) "Listen with Him 💙" else "Listen with Her 💖",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        if (isLiveSyncing) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF4CAF50).copy(alpha = 0.15f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF4CAF50))
              )
              Text(
                text = "Live Synced",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      val live = partnerPlayback
      if (live != null && live.songId.isNotBlank()) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              if (live.thumbnailUrl != null) {
                AsyncImage(
                  model = live.thumbnailUrl,
                  contentDescription = null,
                  modifier = Modifier.fillMaxWidth(),
                  contentScale = ContentScale.Crop
                )
              } else {
                Icon(
                  painter = painterResource(R.drawable.music_note),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary
                )
              }
            }

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = live.title.ifBlank { "Unknown Track" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = live.artist.ifBlank { partnerName },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(Modifier.height(2.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = if (live.isPlaying) "▶ Playing" else "⏸ Paused",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (live.isPlaying) Color(0xFF4CAF50) else Color(0xFFFF9800),
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "•",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = "${formatTime(live.positionMs)} / ${formatTime(live.durationMs)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(vertical = 18.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              painter = painterResource(R.drawable.music_note),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Waiting for $partnerName to play a song...",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isLiveSyncing) {
          Button(
            onClick = {
              CouplePlaybackSyncManager.disconnectSync()
              Toast.makeText(context, "Disjoined from sync", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B))
          ) {
            Icon(
              painter = painterResource(R.drawable.connect_people),
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Disjoin / Stop Sync 💕",
              color = Color.White,
              fontWeight = FontWeight.Bold
            )
          }
        } else {
          if (live != null && live.songId.isNotBlank()) {
            Button(
              onClick = {
                if (spaceId.isBlank()) {
                  Toast.makeText(context, "Pair with your partner first 💕", Toast.LENGTH_SHORT).show()
                } else {
                  CouplePlaybackSyncManager.startLiveSyncFollow(
                    scope = scope,
                    spaceId = spaceId,
                    myRole = role,
                    playerConnection = playerConnection,
                    customUrl = customFirebaseUrl
                  )
                  Toast.makeText(context, "Synced to $partnerName's song 💕", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081))
            ) {
              Icon(
                painter = painterResource(R.drawable.connect_people),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Tune in & Sync with $partnerName 💕",
                color = Color.White,
                fontWeight = FontWeight.Bold
              )
            }
          }

          val meta = playerConnection?.service?.currentMediaMetadata?.value
          Button(
            onClick = {
              if (spaceId.isBlank()) {
                Toast.makeText(context, "Pair with your partner first 💕", Toast.LENGTH_SHORT).show()
              } else if (meta == null) {
                Toast.makeText(context, "Play a song first to invite $partnerName 🎵", Toast.LENGTH_SHORT).show()
              } else {
                val req = CoupleSyncRequest(
                  requestId = System.currentTimeMillis().toString(),
                  requesterRole = role.name,
                  requesterName = if (role == CoupleAuthGuard.CoupleRole.HER) "Her" else "Him",
                  songId = meta.id,
                  title = meta.title,
                  artist = meta.artists.joinToString { it.name },
                  thumbnailUrl = meta.thumbnailUrl,
                  positionMs = playerConnection.player.currentPosition.coerceAtLeast(0L),
                  isPlaying = playerConnection.player.isPlaying,
                  timestamp = System.currentTimeMillis()
                )
                scope.launch {
                  FirebaseCoupleSync.sendSyncRequest(spaceId, req)
                  CouplePlaybackSyncManager.startBroadcaster(
                    scope = scope,
                    spaceId = spaceId,
                    role = role,
                    myName = req.requesterName,
                    playerConnection = playerConnection,
                    customUrl = customFirebaseUrl
                  )
                }
                Toast.makeText(context, "Invitation sent to $partnerName 💕", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary
            )
          ) {
            Icon(
              painter = painterResource(R.drawable.favorite),
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Invite $partnerName to My Song 💕",
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}
