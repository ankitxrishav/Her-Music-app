package com.fenrir.her.couple

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.music.innertube.models.WatchEndpoint
import com.fenrir.her.LocalPlayerAwareWindowInsets
import com.fenrir.her.LocalPlayerConnection
import com.fenrir.her.R
import com.fenrir.her.auth.CoupleAuthGuard
import com.fenrir.her.constants.AccountEmailKey
import com.fenrir.her.constants.CoupleRoleKey
import com.fenrir.her.constants.CoupleMyNameKey
import com.fenrir.her.constants.CouplePartnerNameKey
import com.fenrir.her.constants.CoupleSpaceIdKey
import com.fenrir.her.constants.CoupleFirebaseUrlKey
import com.fenrir.her.constants.CoupleChatMessagesKey
import com.fenrir.her.extensions.metadata
import com.fenrir.her.playback.queues.YouTubeQueue
import com.fenrir.her.utils.rememberPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUICK_LOVE_STICKERS = listOf(
  "❤️",
  "I love you 💕",
  "Listening to our song 🎵",
  "Miss you so much 🥺",
  "Sending a kiss 💋",
  "Forever & Always ✨",
  "Good night my love 🌙",
  "Good morning sunshine ☀️"
)

@Composable
fun CoupleChatView(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val playerConnection = LocalPlayerConnection.current
  val coroutineScope = rememberCoroutineScope()

  val (accountEmail) = rememberPreference(AccountEmailKey, "")
  val (coupleRolePref) = rememberPreference(CoupleRoleKey, "HIM")
  val (customMyName) = rememberPreference(CoupleMyNameKey, "")
  val (customPartnerName) = rememberPreference(CouplePartnerNameKey, "")
  val (spaceId) = rememberPreference(CoupleSpaceIdKey, "")
  val (customFirebaseUrl) = rememberPreference(CoupleFirebaseUrlKey, "")
  var chatMessagesJson by rememberPreference(CoupleChatMessagesKey, "[]")

  val role = remember(accountEmail, coupleRolePref) {
    if (coupleRolePref == "HER" || coupleRolePref == "RITIKA") CoupleAuthGuard.CoupleRole.HER
    else CoupleAuthGuard.CoupleRole.HIM
  }
  val myName = CoupleAuthGuard.getMyName(role, customMyName)
  val partnerName = CoupleAuthGuard.getPartnerName(role, customPartnerName)

  val messages = remember(chatMessagesJson) {
    CoupleSerialization.parseMessages(chatMessagesJson)
  }

  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  val currentMediaMetadata by playerConnection?.service?.currentMediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }

  LaunchedEffect(spaceId) {
    if (spaceId.isNotBlank()) {
      while (true) {
        val remoteMessages = FirebaseCoupleSync.fetchChatMessages(spaceId, customFirebaseUrl)
        if (remoteMessages.isNotEmpty()) {
          val currentIds = messages.map { it.id }.toSet()
          val newMessages = remoteMessages.filter { it.id !in currentIds }
          if (newMessages.isNotEmpty()) {
            val merged = (messages + newMessages).sortedBy { it.timestamp }
            chatMessagesJson = CoupleSerialization.serializeMessages(merged)
          }
        }
        delay(6000)
      }
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  fun sendMessage(text: String, song: SharedSongAttachment? = null) {
    if (text.isBlank() && song == null) return
    val newMsg = CoupleChatMessage(
      sender = myName,
      text = text.trim(),
      song = song
    )
    val updated = messages + newMsg
    chatMessagesJson = CoupleSerialization.serializeMessages(updated)
    inputText = ""
    if (spaceId.isNotBlank()) {
      coroutineScope.launch {
        FirebaseCoupleSync.sendChatMessage(spaceId, newMsg, customFirebaseUrl)
      }
    }
  }

  val playerAwareInsets = LocalPlayerAwareWindowInsets.current
  val bottomInsetPadding = playerAwareInsets.asPaddingValues().calculateBottomPadding()

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(bottom = bottomInsetPadding)
      .imePadding()
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
          ) {
            Text(
              text = if (role == CoupleAuthGuard.CoupleRole.HER) "💙 HimChat • Chatting with $partnerName" else "💖 HerChat • Chatting with $partnerName",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
      }

      if (messages.isEmpty()) {
        item {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              painter = painterResource(R.drawable.favorite),
              contentDescription = null,
              tint = Color(0xFFE91E63),
              modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No messages yet",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Send your first message or song to $partnerName!",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      items(messages, key = { it.id }) { msg ->
        val isMe = msg.sender.equals(myName, ignoreCase = true)
        val timeString = remember(msg.timestamp) {
          SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
          verticalAlignment = Alignment.Bottom
        ) {
          if (!isMe) {
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFFE91E63)),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = partnerName.take(1),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
          }

          Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
          ) {
            Card(
              shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMe) 18.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 18.dp
              ),
              colors = CardDefaults.cardColors(
                containerColor = if (isMe) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.secondaryContainer
                }
              ),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                if (msg.song != null) {
                  Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable {
                        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = msg.song.id)))
                      }
                  ) {
                    Row(
                      modifier = Modifier.padding(8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      AsyncImage(
                        model = msg.song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .size(48.dp)
                          .clip(RoundedCornerShape(8.dp))
                      )
                      Spacer(modifier = Modifier.width(10.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = msg.song.title,
                          style = MaterialTheme.typography.bodyMedium,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        Text(
                          text = msg.song.artist,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                          shape = RoundedCornerShape(4.dp),
                          color = Color(0xFFE91E63).copy(alpha = 0.15f),
                          modifier = Modifier.padding(top = 2.dp)
                        ) {
                          Text(
                            text = "🎵 Tap to Play",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE91E63),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                          )
                        }
                      }
                      Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                }

                if (msg.text.isNotBlank()) {
                  Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                  )
                }
              }
            }

            Text(
              text = timeString,
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
          }

          if (isMe) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = myName.take(1),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }
        }
      }
    }

    AnimatedVisibility(visible = currentMediaMetadata != null) {
      currentMediaMetadata?.let { song ->
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Playing: ${song.title}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            FilledIconButton(
              onClick = {
                coroutineScope.launch {
                  CoupleSongShareHelper.shareMediaMetadata(context, song, "Listen to this with me ❤️")
                }
              },
              colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFFE91E63)
              ),
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = "Share Current Song",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      QUICK_LOVE_STICKERS.forEach { sticker ->
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
          modifier = Modifier.clickable { sendMessage(sticker) }
        ) {
          Text(
            text = sticker,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
          )
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = inputText,
        onValueChange = { inputText = it },
        placeholder = { Text("Write to $partnerName...") },
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          focusedBorderColor = Color(0xFFE91E63)
        ),
        modifier = Modifier.weight(1f),
        maxLines = 4
      )
      Spacer(modifier = Modifier.width(8.dp))
      FilledIconButton(
        onClick = { sendMessage(inputText) },
        enabled = inputText.isNotBlank(),
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = Color(0xFFE91E63)
        ),
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          painter = painterResource(R.drawable.send_chat),
          contentDescription = "Send",
          tint = Color.White,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}
