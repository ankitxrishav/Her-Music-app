package com.fenrir.her.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.asPaddingValues
import coil3.compose.AsyncImage
import com.music.innertube.models.WatchEndpoint
import com.fenrir.her.LocalPlayerAwareWindowInsets
import com.fenrir.her.LocalPlayerConnection
import com.fenrir.her.R
import com.fenrir.her.auth.CoupleAuthGuard
import com.fenrir.her.constants.AccountEmailKey
import com.fenrir.her.constants.CoupleRoleKey
import com.fenrir.her.constants.CoupleMyNameKey
import com.fenrir.her.constants.CouplePartnerNameKey
import com.fenrir.her.constants.CoupleSharedSongsKey
import com.fenrir.her.playback.queues.YouTubeQueue
import com.fenrir.her.utils.rememberPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CoupleSharedSongsView(
  modifier: Modifier = Modifier,
) {
  val playerConnection = LocalPlayerConnection.current

  val (accountEmail) = rememberPreference(AccountEmailKey, "")
  val (coupleRolePref) = rememberPreference(CoupleRoleKey, "HIM")
  val (customMyName) = rememberPreference(CoupleMyNameKey, "")
  val (customPartnerName) = rememberPreference(CouplePartnerNameKey, "")
  var sharedSongsJson by rememberPreference(CoupleSharedSongsKey, "[]")

  val role = remember(accountEmail, coupleRolePref) {
    if (coupleRolePref == "HER" || coupleRolePref == "RITIKA") CoupleAuthGuard.CoupleRole.HER
    else CoupleAuthGuard.CoupleRole.HIM
  }
  val myName = CoupleAuthGuard.getMyName(role, customMyName)
  val partnerName = CoupleAuthGuard.getPartnerName(role, customPartnerName)

  val sharedSongs = remember(sharedSongsJson) {
    CoupleSerialization.parseSharedSongs(sharedSongsJson)
  }

  Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    if (sharedSongs.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Shared Music Vault",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "${sharedSongs.size} tracks exchanged with love",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Button(
          onClick = {
            sharedSongs.firstOrNull()?.let { firstTrack ->
              playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = firstTrack.id)))
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE91E63)
          ),
          shape = RoundedCornerShape(20.dp)
        ) {
          Icon(
            painter = painterResource(R.drawable.play),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Play All")
        }
      }
    }

    if (sharedSongs.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          painter = painterResource(R.drawable.music_note),
          contentDescription = null,
          tint = Color(0xFFE91E63).copy(alpha = 0.5f),
          modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "No Shared Songs Yet",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Tap the 3 dots on any song in the app and choose \"Send to $partnerName\"!",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          modifier = Modifier.padding(horizontal = 24.dp)
        )
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize()
      ) {
        items(sharedSongs, key = { it.id + it.timestamp }) { song ->
          val timeStr = remember(song.timestamp) {
            SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault()).format(Date(song.timestamp))
          }

          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = song.id)))
              }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(56.dp)
                  .clip(RoundedCornerShape(12.dp))
              )

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = song.title,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = song.artist,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )

                if (!song.note.isNullOrBlank()) {
                  Text(
                    text = "\"${song.note}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE91E63),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                  )
                }

                Row(
                  modifier = Modifier.padding(top = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFE91E63).copy(alpha = 0.15f)
                  ) {
                    Text(
                      text = "From ${song.addedBy} ❤️",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = Color(0xFFE91E63),
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }

                  Spacer(modifier = Modifier.width(8.dp))

                  Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                  )
                }
              }

              IconButton(
                onClick = {
                  val updated = sharedSongs.toMutableList()
                  updated.removeAll { it.id == song.id && it.timestamp == song.timestamp }
                  sharedSongsJson = CoupleSerialization.serializeSharedSongs(updated)
                }
              ) {
                Icon(
                  painter = painterResource(R.drawable.close),
                  contentDescription = "Remove",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}
