package com.fenrir.her.couple

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class CoupleAuraProfile(
  val name: String,
  val harmonicFrequency: String,
  val resonanceScore: Int,
  val weekDescriptor: String,
  val colors: List<Color>,
  val particleCount: Int = 28,
  val spinVelocity: Float = 1.0f
)

object LatentSpaceAuraClustering {
  fun computeWeeklyAura(totalEventsThisWeek: Int = 42): CoupleAuraProfile {
    val cal = Calendar.getInstance()
    val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
    val clusterIndex = (weekOfYear + (totalEventsThisWeek / 7)) % 4

    return when (clusterIndex) {
      0 -> CoupleAuraProfile(
        name = "Velvet Twilight",
        harmonicFrequency = "528 Hz • Solfeggio Love",
        resonanceScore = (94..99).random(Random(weekOfYear.toLong())),
        weekDescriptor = "Late-night acoustic ballads & nocturnal whispers",
        colors = listOf(
          Color(0xFF8A2BE2),
          Color(0xFF4A148C),
          Color(0xFFFF4081),
          Color(0xFF1A237E)
        ),
        spinVelocity = 0.8f
      )
      1 -> CoupleAuraProfile(
        name = "Solar Ember",
        harmonicFrequency = "432 Hz • Natural Resonance",
        resonanceScore = (93..98).random(Random(weekOfYear.toLong())),
        weekDescriptor = "Warm sunlit road trips & rhythmic pop warmth",
        colors = listOf(
          Color(0xFFFF6D00),
          Color(0xFFFF3D00),
          Color(0xFFFFD600),
          Color(0xFFDD2C00)
        ),
        spinVelocity = 1.2f
      )
      2 -> CoupleAuraProfile(
        name = "Ethereal Nebula",
        harmonicFrequency = "639 Hz • Harmonic Connection",
        resonanceScore = (95..99).random(Random(weekOfYear.toLong())),
        weekDescriptor = "Dreamy indie reverberations & soulful slow-tempo",
        colors = listOf(
          Color(0xFF00E5FF),
          Color(0xFF00B0FF),
          Color(0xFF7C4DFF),
          Color(0xFF1DE9B6)
        ),
        spinVelocity = 0.7f
      )
      else -> CoupleAuraProfile(
        name = "Rose Quartz Reverie",
        harmonicFrequency = "528 Hz • Miracle Resonance",
        resonanceScore = (96..100).random(Random(weekOfYear.toLong())),
        weekDescriptor = "Pure heartfelt love notes & intimate harmonies",
        colors = listOf(
          Color(0xFFFF4081),
          Color(0xFFF50057),
          Color(0xFFFF80AB),
          Color(0xFFFFE082)
        ),
        spinVelocity = 1.0f
      )
    }
  }
}

private class AuraNode(
  val radiusRatio: Float,
  val angleOffset: Float,
  val size: Float,
  val color: Color
)

@Composable
fun LatentSpaceAuraCanvas(
  profile: CoupleAuraProfile,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "auraMotion")
  val rotationAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 6.28318f,
    animationSpec = infiniteRepeatable(
      animation = tween(24000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "auraRotation"
  )

  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.88f,
    targetValue = 1.12f,
    animationSpec = infiniteRepeatable(
      animation = tween(3800, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "auraPulse"
  )

  val nodes = remember(profile) {
    List(profile.particleCount) { index ->
      AuraNode(
        radiusRatio = 0.25f + (index % 4) * 0.18f,
        angleOffset = (index * 6.28318f / profile.particleCount),
        size = Random.nextFloat() * 6f + 3f,
        color = profile.colors[index % profile.colors.size]
      )
    }
  }

  Canvas(modifier = modifier) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = (minOf(size.width, size.height) / 2f) * pulseScale

    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          profile.colors[0].copy(alpha = 0.45f),
          profile.colors[1].copy(alpha = 0.25f),
          Color.Transparent
        ),
        center = center,
        radius = maxRadius * 0.85f
      ),
      radius = maxRadius * 0.85f,
      center = center
    )

    val points = nodes.map { node ->
      val angle = rotationAngle * profile.spinVelocity + node.angleOffset
      val r = maxRadius * node.radiusRatio
      val x = center.x + r * cos(angle)
      val y = center.y + r * sin(angle)
      Offset(x, y) to node
    }

    for (i in points.indices) {
      for (j in i + 1 until points.size) {
        val (p1, _) = points[i]
        val (p2, _) = points[j]
        val dist = (p1 - p2).getDistance()
        if (dist < maxRadius * 0.35f) {
          val alpha = (1f - (dist / (maxRadius * 0.35f))) * 0.35f
          drawLine(
            color = profile.colors[0].copy(alpha = alpha),
            start = p1,
            end = p2,
            strokeWidth = 1.2.dp.toPx()
          )
        }
      }
    }

    points.forEach { (pos, node) ->
      drawCircle(
        color = node.color.copy(alpha = 0.85f),
        radius = node.size,
        center = pos
      )
    }
  }
}

@Composable
fun CoupleAuraCard(
  profile: CoupleAuraProfile,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(28.dp))
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0xFF120B1A),
              Color(0xFF0C0712),
              Color(0xFF190C22)
            )
          )
        )
        .border(
          width = 1.dp,
          brush = Brush.horizontalGradient(profile.colors),
          shape = RoundedCornerShape(28.dp)
        )
        .padding(20.dp)
    ) {
      LatentSpaceAuraCanvas(
        profile = profile,
        modifier = Modifier
          .size(160.dp)
          .align(Alignment.CenterEnd)
      )

      Column(modifier = Modifier.fillMaxWidth(0.68f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = profile.colors[0],
            modifier = Modifier.size(18.dp)
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Shared Aura • This Week",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = profile.colors[0]
          )
        }

        Spacer(Modifier.height(8.dp))

        Text(
          text = profile.name,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )

        Spacer(Modifier.height(4.dp))

        Text(
          text = profile.weekDescriptor,
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.7f),
          lineHeight = 16.sp
        )

        Spacer(Modifier.height(14.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "✨ ${profile.resonanceScore}% Resonance",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.12f))
              .padding(horizontal = 10.dp, vertical = 4.dp)
          )

          Text(
            text = profile.harmonicFrequency.substringBefore(" •"),
            style = MaterialTheme.typography.labelSmall,
            color = profile.colors[0],
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(profile.colors[0].copy(alpha = 0.15f))
              .padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }
    }
  }
}
