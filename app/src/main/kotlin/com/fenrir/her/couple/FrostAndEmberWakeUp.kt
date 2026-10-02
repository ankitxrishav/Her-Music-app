package com.fenrir.her.couple

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
data class FrostEmberState(
  val isPartnerActive: Boolean = false,
  val partnerName: String = "Partner",
  val partnerTrack: String? = null,
  val partnerArtist: String? = null,
  val lastWakeUpMs: Long = 0L
)

object FrostEmberSerialization {
  private val json = Json { ignoreUnknownKeys = true }

  fun parse(rawJson: String): FrostEmberState {
    return try {
      json.decodeFromString<FrostEmberState>(rawJson)
    } catch (_: Exception) {
      FrostEmberState()
    }
  }

  fun serialize(state: FrostEmberState): String {
    return try {
      json.encodeToString(state)
    } catch (_: Exception) {
      "{}"
    }
  }
}

private class EmberSpark(
  val initialX: Float,
  val speedY: Float,
  val size: Float,
  val phase: Float,
  val color: Color
)

@Composable
fun FrostAndEmberBackdrop(
  isPartnerActive: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val thawProgress by animateFloatAsState(
    targetValue = if (isPartnerActive) 1f else 0f,
    animationSpec = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
    label = "emberThawProgress"
  )

  Box(modifier = modifier.fillMaxSize().background(Color(0xFF060709))) {
    if (thawProgress > 0.01f) {
      EmberCanvasEffect(thawProgress = thawProgress)
    }
    content()
  }
}

@Composable
private fun EmberCanvasEffect(thawProgress: Float) {
  val infiniteTransition = rememberInfiniteTransition(label = "emberPulse")
  val breathPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 6.28318f,
    animationSpec = infiniteRepeatable(
      animation = tween(4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "breathPhase"
  )

  val sparks = remember {
    List(24) {
      EmberSpark(
        initialX = Random.nextFloat(),
        speedY = Random.nextFloat() * 0.4f + 0.2f,
        size = Random.nextFloat() * 5f + 3f,
        phase = Random.nextFloat() * 6.28f,
        color = if (Random.nextBoolean()) Color(0xFFFF9100) else Color(0xFFFF3D00)
      )
    }
  }

  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val emberHeight = h * (0.35f * thawProgress)
    val pulseFactor = 0.85f + 0.15f * sin(breathPhase)

    val emberBrush = Brush.verticalGradient(
      0.0f to Color.Transparent,
      0.4f to Color(0x22FF6F00).copy(alpha = 0.25f * thawProgress * pulseFactor),
      0.7f to Color(0x66FF3D00).copy(alpha = 0.45f * thawProgress * pulseFactor),
      1.0f to Color(0xCCFF5722).copy(alpha = 0.75f * thawProgress * pulseFactor),
      startY = h - emberHeight,
      endY = h
    )

    drawRect(
      brush = emberBrush,
      topLeft = Offset(0f, h - emberHeight),
      size = androidx.compose.ui.geometry.Size(w, emberHeight)
    )

    sparks.forEach { spark ->
      val yProgress = (breathPhase * spark.speedY + spark.phase) % 1f
      val sparkY = h - (yProgress * emberHeight)
      val sway = sin(breathPhase * 2f + spark.phase) * 20f
      val sparkX = (spark.initialX * w + sway).coerceIn(0f, w)

      val alpha = (1f - yProgress) * thawProgress * pulseFactor
      drawCircle(
        color = spark.color.copy(alpha = alpha.coerceIn(0f, 1f)),
        radius = spark.size,
        center = Offset(sparkX, sparkY)
      )
    }
  }
}

@Composable
fun FrostAndEmberWhisperBanner(
  state: FrostEmberState,
  onTap: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = state.isPartnerActive && !state.partnerTrack.isNullOrBlank(),
    enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { -it },
    exit = fadeOut(tween(500)) + slideOutVertically(tween(500)) { -it },
    modifier = modifier
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(
          Brush.horizontalGradient(
            colors = listOf(
              Color(0xDD2D1005),
              Color(0xEE1E0B05),
              Color(0xDD3A1308)
            )
          )
        )
        .border(
          width = 1.dp,
          brush = Brush.horizontalGradient(
            listOf(Color(0xFFFF9100), Color(0xFFFF3D00), Color(0xFFFFAB40))
          ),
          shape = RoundedCornerShape(20.dp)
        )
        .clickable { onTap() }
        .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Text(text = "🔥", fontSize = 18.sp)
          Spacer(Modifier.width(10.dp))
          androidx.compose.foundation.layout.Column {
            Text(
              text = "${state.partnerName}'s ember has awakened",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFFFAB40)
            )
            state.partnerTrack?.let { track ->
              Text(
                text = "Listening to $track",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1
              )
            }
          }
        }

        Text(
          text = "Shared Space",
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFFFFAB40),
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33FF9100))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        )
      }
    }
  }
}
