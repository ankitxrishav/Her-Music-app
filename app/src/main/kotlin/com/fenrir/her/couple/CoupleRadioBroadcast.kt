package com.fenrir.her.couple

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fenrir.her.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sin
import kotlin.random.Random

@Serializable
data class RadioBroadcastSession(
  val isBroadcasting: Boolean = false,
  val broadcasterRole: String = "HIM",
  val broadcasterName: String = "Him",
  val songId: String = "",
  val songTitle: String = "",
  val songArtist: String = "",
  val artworkUrl: String? = null,
  val timestampMs: Long = 0L,
  val startedEpochMs: Long = 0L,
  val heartCount: Int = 0,
  val lastHeartEpochMs: Long = 0L
)

object RadioBroadcastSerialization {
  private val json = Json { ignoreUnknownKeys = true }

  fun parse(rawJson: String): RadioBroadcastSession {
    return try {
      json.decodeFromString<RadioBroadcastSession>(rawJson)
    } catch (_: Exception) {
      RadioBroadcastSession()
    }
  }

  fun serialize(session: RadioBroadcastSession): String {
    return try {
      json.encodeToString(session)
    } catch (_: Exception) {
      "{}"
    }
  }
}

@Composable
fun RadioBroadcastLiveBanner(
  session: RadioBroadcastSession,
  isMeBroadcasting: Boolean,
  onTuneIn: () -> Unit,
  onStopBroadcast: () -> Unit,
  onSendHeart: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = session.isBroadcasting && session.songTitle.isNotBlank(),
    enter = fadeIn(tween(400)) + slideInVertically(tween(400)),
    exit = fadeOut(tween(300)) + slideOutVertically(tween(300)),
    modifier = modifier
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(
          Brush.horizontalGradient(
            colors = if (isMeBroadcasting) listOf(
              Color(0xFF2A0845),
              Color(0xFF6441A5)
            ) else listOf(
              Color(0xFF1F072B),
              Color(0xFF880E4F),
              Color(0xFF4A148C)
            )
          )
        )
        .border(
          width = 1.dp,
          brush = Brush.horizontalGradient(
            listOf(Color(0xFFFF4081), Color(0xFFE040FB), Color(0xFFFF80AB))
          ),
          shape = RoundedCornerShape(24.dp)
        )
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF1744))
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = if (isMeBroadcasting) "LIVE BROADCAST ACTIVE" else "${session.broadcasterName.uppercase()} IS BROADCASTING LIVE",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFFF80AB),
              letterSpacing = 1.sp
            )
          }

          Spacer(Modifier.height(4.dp))

          Text(
            text = session.songTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          Text(
            text = session.songArtist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          if (session.heartCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
              text = "❤️ ${session.heartCount} hearts received",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFFFF80AB)
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (isMeBroadcasting) {
            OutlinedButton(
              onClick = onStopBroadcast,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              shape = RoundedCornerShape(16.dp)
            ) {
              Text("End")
            }
          } else {
            FilledIconButton(
              onClick = onSendHeart,
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x33FF4081)),
              modifier = Modifier.size(44.dp)
            ) {
              Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = "Send Heart",
                tint = Color(0xFFFF4081),
                modifier = Modifier.size(22.dp)
              )
            }

            Button(
              onClick = onTuneIn,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
              shape = RoundedCornerShape(16.dp)
            ) {
              Text("Tune In", fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }
    }
  }
}

private class FloatingHeart(
  val startXFraction: Float,
  val size: Float,
  val speed: Float,
  val swayPhase: Float
)

@Composable
fun FloatingHeartBurstOverlay(
  heartBurstEpochMs: Long,
  modifier: Modifier = Modifier
) {
  val isRecent = remember(heartBurstEpochMs) {
    (System.currentTimeMillis() - heartBurstEpochMs) < 4500L
  }

  if (!isRecent) return

  val infiniteTransition = rememberInfiniteTransition(label = "floatingHearts")
  val progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(3500, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "heartRise"
  )

  val hearts = remember {
    List(12) {
      FloatingHeart(
        startXFraction = 0.2f + Random.nextFloat() * 0.6f,
        size = 18f + Random.nextFloat() * 16f,
        speed = 0.6f + Random.nextFloat() * 0.4f,
        swayPhase = Random.nextFloat() * 6.28f
      )
    }
  }

  Canvas(modifier = modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height

    hearts.forEach { heart ->
      val individualProgress = ((progress * heart.speed) + heart.swayPhase / 6.28f) % 1f
      val y = h - (individualProgress * h)
      val sway = sin(individualProgress * 12f + heart.swayPhase) * 30f
      val x = (heart.startXFraction * w + sway).coerceIn(10f, w - 10f)
      val alpha = ((1f - individualProgress) * 1.5f).coerceIn(0f, 1f)

      drawHeart(
        center = Offset(x, y),
        size = heart.size,
        color = Color(0xFFFF4081).copy(alpha = alpha)
      )
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(
  center: Offset,
  size: Float,
  color: Color
) {
  val path = Path().apply {
    val s = size / 2f
    moveTo(center.x, center.y + s * 0.8f)
    cubicTo(
      center.x - s * 1.5f, center.y - s * 0.4f,
      center.x - s, center.y - s * 1.3f,
      center.x, center.y - s * 0.5f
    )
    cubicTo(
      center.x + s, center.y - s * 1.3f,
      center.x + s * 1.5f, center.y - s * 0.4f,
      center.x, center.y + s * 0.8f
    )
    close()
  }
  drawPath(path = path, color = color)
}
