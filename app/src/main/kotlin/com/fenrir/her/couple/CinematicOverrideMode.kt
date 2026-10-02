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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fenrir.her.R
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private class CinematicParticle(
  val x: Float,
  val y: Float,
  val radius: Float,
  val alpha: Float,
  val speed: Float
)

@Composable
fun CinematicOverridePlayer(
  isPlaying: Boolean,
  title: String,
  artist: String,
  artworkUrl: String?,
  onTogglePlayPause: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onDismiss: () -> Unit,
  onSendReaction: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showControls by remember { mutableStateOf(false) }

  LaunchedEffect(showControls) {
    if (showControls) {
      delay(4000)
      showControls = false
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "cinematicMotion")
  val zoomScale by infiniteTransition.animateFloat(
    initialValue = 1.05f,
    targetValue = 1.22f,
    animationSpec = infiniteRepeatable(
      animation = tween(22000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "zoomScale"
  )

  val panOffset by infiniteTransition.animateFloat(
    initialValue = -35f,
    targetValue = 35f,
    animationSpec = infiniteRepeatable(
      animation = tween(28000, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "panOffset"
  )

  val lightLeakPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 6.28f,
    animationSpec = infiniteRepeatable(
      animation = tween(12000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "lightLeak"
  )

  val particles = remember {
    List(20) {
      CinematicParticle(
        x = Random.nextFloat(),
        y = Random.nextFloat(),
        radius = Random.nextFloat() * 6f + 2f,
        alpha = Random.nextFloat() * 0.35f + 0.1f,
        speed = Random.nextFloat() * 0.3f + 0.1f
      )
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) { showControls = !showControls }
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          scaleX = zoomScale
          scaleY = zoomScale
          translationX = panOffset
        }
    ) {
      if (!artworkUrl.isNullOrBlank()) {
        AsyncImage(
          model = artworkUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.radialGradient(
                colors = listOf(Color(0xFF2C103A), Color(0xFF0F0615), Color(0xFF030105))
              )
            )
        )
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.35f))
      )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      val leakX = w * (0.5f + 0.4f * sin(lightLeakPhase))
      val leakY = h * 0.3f
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color(0x33FFB300),
            Color(0x15FF7043),
            Color.Transparent
          ),
          center = Offset(leakX, leakY),
          radius = w * 0.7f
        ),
        radius = w * 0.7f,
        center = Offset(leakX, leakY)
      )

      particles.forEach { p ->
        val yProg = (p.y + lightLeakPhase * p.speed) % 1f
        val px = (p.x * w + sin(lightLeakPhase + p.x * 6f) * 15f).coerceIn(0f, w)
        val py = yProg * h
        drawCircle(
          color = Color(0xFFFFD54F).copy(alpha = p.alpha),
          radius = p.radius,
          center = Offset(px, py)
        )
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxSize(0.14f)
        .align(Alignment.TopCenter)
        .background(Color.Black)
    )

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxSize(0.16f)
        .align(Alignment.BottomCenter)
        .background(Color.Black)
    )

    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
          fontSize = 20.sp,
          letterSpacing = 1.5.sp,
          fontWeight = FontWeight.Medium
        ),
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = artist,
        style = MaterialTheme.typography.bodyMedium.copy(
          letterSpacing = 2.sp,
          fontSize = 13.sp
        ),
        color = Color.White.copy(alpha = 0.65f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    AnimatedVisibility(
      visible = showControls,
      enter = fadeIn(tween(300)),
      exit = fadeOut(tween(300)),
      modifier = Modifier.fillMaxSize()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.45f))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 20.dp, end = 20.dp)
            .align(Alignment.TopCenter),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White.copy(alpha = 0.15f))
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Icon(
              painter = painterResource(R.drawable.favorite),
              contentDescription = null,
              tint = Color(0xFFFF4081),
              modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Cinematic Override",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              fontWeight = FontWeight.Bold
            )
          }

          FilledIconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              painter = painterResource(R.drawable.close),
              contentDescription = "Exit",
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Row(
          modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(28.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledIconButton(
            onClick = onPrevious,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.size(48.dp)
          ) {
            Icon(
              painter = painterResource(R.drawable.skip_previous),
              contentDescription = "Previous",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }

          FilledIconButton(
            onClick = onTogglePlayPause,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color.White
            ),
            modifier = Modifier.size(64.dp)
          ) {
            Icon(
              painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
              contentDescription = "Play/Pause",
              tint = Color.Black,
              modifier = Modifier.size(32.dp)
            )
          }

          FilledIconButton(
            onClick = onNext,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.size(48.dp)
          ) {
            Icon(
              painter = painterResource(R.drawable.skip_next),
              contentDescription = "Next",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        FilledIconButton(
          onClick = onSendReaction,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0xFFFF4081)
          ),
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 60.dp, end = 24.dp)
            .size(52.dp)
        ) {
          Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = "Send Love",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
          )
        }
      }
    }
  }
}
