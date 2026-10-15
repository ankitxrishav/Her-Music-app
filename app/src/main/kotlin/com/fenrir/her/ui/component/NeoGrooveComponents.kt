package com.fenrir.her.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fenrir.her.R

@Composable
fun NeoCircleButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
    backgroundColor: Color = Color.Transparent,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    isActive: Boolean = false,
    activeBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    activeTintColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "neoCircleScale",
    )

    val currentBg = if (isActive) activeBackgroundColor else backgroundColor
    val currentTint = if (isActive) activeTintColor else tint

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(currentBg)
            .border(1.5.dp, if (isActive) activeBackgroundColor else borderColor, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = currentTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun NeoCircleButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
    backgroundColor: Color = Color.Transparent,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    isActive: Boolean = false,
    activeBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    activeTintColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "neoCircleScaleVec",
    )

    val currentBg = if (isActive) activeBackgroundColor else backgroundColor
    val currentTint = if (isActive) activeTintColor else tint

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(currentBg)
            .border(1.5.dp, if (isActive) activeBackgroundColor else borderColor, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = currentTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun NeoVinylRecord(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val angle = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
        val animatedAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(24_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "vinylAngle",
        )
        animatedAngle
    } else {
        0f
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationZ = angle
            },
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f
                    val baseColor = Color(0xFF141416)
                    val grooveColor = Color.White.copy(alpha = 0.07f)
                    val grooveStroke = Stroke(width = 0.9f)
                    val grooveStep = radius * 0.055f
                    val startR = radius * 0.40f
                    val endR = radius * 0.94f
                    val sweepBrush = Brush.sweepGradient(
                        0.0f to Color.White.copy(alpha = 0.12f),
                        0.25f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.12f),
                        0.75f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = 0.12f),
                        center = center,
                    )
                    val arcSize = Size(radius * 2, radius * 2)
                    val arcTopLeft = Offset(center.x - radius, center.y - radius)

                    onDrawBehind {
                        drawCircle(
                            color = baseColor,
                            radius = radius,
                            center = center,
                        )
                        var currR = startR
                        while (currR < endR) {
                            drawCircle(
                                color = grooveColor,
                                radius = currR,
                                center = center,
                                style = grooveStroke,
                            )
                            currR += grooveStep
                        }
                        drawArc(
                            brush = sweepBrush,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = true,
                            size = arcSize,
                            topLeft = arcTopLeft,
                        )
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize(0.38f)
                .clip(CircleShape)
                .background(Color(0xFFE11D48)),
            contentAlignment = Alignment.Center,
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            )
        }
    }
}

@Composable
fun NeoWaveformSeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 42,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val rawProgress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val currentProgress = if (isDragging) dragProgress else rawProgress

    val barHeights = remember {
        val pattern = floatArrayOf(
            0.25f, 0.40f, 0.65f, 0.45f, 0.85f, 0.60f, 0.95f, 0.70f, 0.50f, 0.80f,
            0.35f, 0.60f, 0.90f, 0.75f, 0.40f, 0.85f, 0.95f, 0.60f, 0.70f, 0.80f,
            0.45f, 0.90f, 0.75f, 0.55f, 0.85f, 0.65f, 0.40f, 0.70f, 0.90f, 0.60f,
            0.80f, 0.45f, 0.65f, 0.85f, 0.50f, 0.75f, 0.90f, 0.60f, 0.40f, 0.55f,
            0.35f, 0.25f,
        )
        FloatArray(barCount) { idx -> pattern[idx % pattern.size] }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeek((ratio * durationMs.coerceAtLeast(0L)).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek((dragProgress * durationMs.coerceAtLeast(0L)).toLong())
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                    ) { change, _ ->
                        dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        change.consume()
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val totalBars = barCount
                val barWidth = (canvasWidth / totalBars) * 0.62f
                val spacing = (canvasWidth - barWidth * totalBars) / (totalBars - 1).coerceAtLeast(1)

                for (i in 0 until totalBars) {
                    val x = i * (barWidth + spacing)
                    val barNormHeight = barHeights[i]
                    val h = canvasHeight * barNormHeight
                    val y = (canvasHeight - h) / 2f
                    val barCenterRatio = (x + barWidth / 2f) / canvasWidth
                    val isPlayed = barCenterRatio <= currentProgress

                    drawRoundRect(
                        color = if (isPlayed) activeColor else inactiveColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }

                val thumbX = canvasWidth * currentProgress
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(thumbX, canvasHeight / 2f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val displayPos = if (isDragging) (dragProgress * durationMs.coerceAtLeast(0L)).toLong() else positionMs
            Text(
                text = formatMillis(displayPos),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatMillis(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun NeoPlayingNextCapsule(
    nextTitle: String,
    nextArtist: String,
    nextArtworkUrl: String?,
    queuePosition: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(38.dp),
                ) {
                    if (!nextArtworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = nextArtworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "PLAYING NEXT",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = "$nextTitle ($nextArtist)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = "IN QUEUE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = queuePosition,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun NeoVinylSleeveArtwork(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val slideOffset by animateFloatAsState(
        targetValue = if (isPlaying) 0.28f else 0.08f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "vinylSlide",
    )

    val sleeveSize = 256.dp

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(sleeveSize)
                .graphicsLayer {
                    translationX = (sleeveSize * slideOffset * 0.70f).toPx()
                },
            contentAlignment = Alignment.Center,
        ) {
            NeoVinylRecord(
                artworkUrl = artworkUrl,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(sleeveSize)
                .graphicsLayer {
                    translationX = (-sleeveSize * slideOffset * 0.12f).toPx()
                }
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.album),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun NeoTogetherPill(
    partnerName: String?,
    isPaired: Boolean,
    onClick: () -> Unit,
    onDisjoinClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isLiveSyncing by com.fenrir.her.couple.CouplePlaybackSyncManager.isLiveSyncing.collectAsState()
    var showDisjoinDialog by remember { mutableStateOf(false) }

    if (showDisjoinDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDisjoinDialog = false },
            title = {
                Text("Listen Together 💕", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("You are currently synced in real time. Do you want to disjoin and stop syncing?")
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showDisjoinDialog = false
                        com.fenrir.her.couple.CouplePlaybackSyncManager.disconnectSync()
                        onDisjoinClick?.invoke()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B))
                ) {
                    Text("Disjoin", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDisjoinDialog = false }) {
                    Text("Stay Synced")
                }
            }
        )
    }

    Surface(
        onClick = {
            if (isLiveSyncing) {
                showDisjoinDialog = true
            } else {
                onClick()
            }
        },
        shape = RoundedCornerShape(24.dp),
        color = if (isLiveSyncing) Color(0xFFE8F5E9) else Color(0xFFE4F0FC),
        border = BorderStroke(1.dp, if (isLiveSyncing) Color(0xFFA5D6A7) else Color(0xFFBEDBFA)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isLiveSyncing) Color(0xFFC8E6C9) else Color(0xFFD0E4FA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isLiveSyncing) R.drawable.connect_people else R.drawable.headset_applemusic),
                    contentDescription = null,
                    tint = if (isLiveSyncing) Color(0xFF2E7D32) else Color(0xFF1E293B),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isLiveSyncing -> "🟢 Synced with ${partnerName?.ifBlank { "partner" } ?: "partner"}"
                        isPaired && !partnerName.isNullOrBlank() -> "$partnerName is listening..."
                        else -> "Her is listening..."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    ),
                    color = if (isLiveSyncing) Color(0xFF1B5E20) else Color(0xFF1E293B),
                )
                Text(
                    text = if (isLiveSyncing) "Tap to disjoin • In sync" else "Together feels better",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = if (isLiveSyncing) Color(0xFF388E3C) else Color(0xFF64748B),
                    ),
                )
            }
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = if (isLiveSyncing) Color(0xFFE91E63) else Color(0xFF1E293B),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun NeoSleekProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF141416),
    inactiveColor: Color = Color(0xFF141416).copy(alpha = 0.2f),
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val rawProgress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val currentProgress = if (isDragging) dragProgress else rawProgress

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeek((ratio * durationMs.coerceAtLeast(0L)).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek((dragProgress * durationMs.coerceAtLeast(0L)).toLong())
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                    ) { change, _ ->
                        dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                val w = size.width
                val h = size.height
                val progressX = w * currentProgress

                drawRoundRect(
                    color = inactiveColor,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                )
                drawRoundRect(
                    color = activeColor,
                    size = Size(progressX, h),
                    cornerRadius = CornerRadius(h / 2f, h / 2f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMillis(positionMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = activeColor.copy(alpha = 0.7f),
                ),
            )
            Text(
                text = formatMillis(durationMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = activeColor.copy(alpha = 0.7f),
                ),
            )
        }
    }
}

fun getDynamicGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
fun NeoHomeHeader(
    avatarUrl: String?,
    userName: String = "Friend",
    partnerName: String? = null,
    myMoodEmoji: String? = null,
    myMoodText: String? = null,
    partnerMoodEmoji: String? = null,
    partnerMoodText: String? = null,
    isPaired: Boolean = false,
    onMoodClick: () -> Unit = {},
    onSearchClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAvatarClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "her",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    letterSpacing = (-1).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeoCircleButton(
                    painter = painterResource(R.drawable.search),
                    contentDescription = "Search",
                    onClick = onSearchClick,
                    size = 40.dp,
                    iconSize = 20.dp,
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), CircleShape)
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.account),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        val headerGreeting = "${getDynamicGreeting()} ♡"

        Text(
            text = headerGreeting,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 34.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            onClick = onMoodClick,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val emoji = myMoodEmoji?.takeIf { it.isNotBlank() } ?: "✨"
                val text = myMoodText?.takeIf { it.isNotBlank() } ?: "Same sky • Same songs • Forever us"
                Text(text = emoji, fontSize = 14.sp)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (isPaired && !partnerMoodText.isNullOrBlank() && !partnerName.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(text = partnerMoodEmoji ?: "💕", fontSize = 13.sp)
                Text(
                    text = "$partnerName is feeling $partnerMoodText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

data class MoodPreset(val emoji: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleMoodPickerSheet(
    currentEmoji: String,
    currentText: String,
    partnerName: String?,
    onDismiss: () -> Unit,
    onSaveMood: (emoji: String, text: String) -> Unit,
) {
    val presets = remember {
        listOf(
            MoodPreset("💕", "In love & romantic"),
            MoodPreset("🎧", "Chilling with vibes"),
            MoodPreset("⚡", "Energetic & pumped"),
            MoodPreset("🌧️", "A little melancholy"),
            MoodPreset("🥺", "Missing you"),
            MoodPreset("✨", "Happy & joyful"),
            MoodPreset("😴", "Cozy & sleepy"),
            MoodPreset("🎯", "Focused & working"),
        )
    }

    var selectedEmoji by remember { mutableStateOf(currentEmoji.ifBlank { "💕" }) }
    var moodInput by remember { mutableStateOf(currentText) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Update Your Mood",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (!partnerName.isNullOrBlank()) "Share your current vibe with $partnerName 💕" else "Set your current listening vibe",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(presets) { preset ->
                    val isSelected = selectedEmoji == preset.emoji
                    Surface(
                        onClick = {
                            selectedEmoji = preset.emoji
                            moodInput = preset.text
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.height(44.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = preset.emoji, fontSize = 16.sp)
                            Text(
                                text = preset.text,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = moodInput,
                onValueChange = { moodInput = it },
                label = { Text("Custom Mood / Thoughts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )

            Button(
                onClick = {
                    val finalEmoji = selectedEmoji.ifBlank { "✨" }
                    val finalText = moodInput.trim().ifBlank { "Good vibes" }
                    onSaveMood(finalEmoji, finalText)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    text = if (!partnerName.isNullOrBlank()) "Share with $partnerName 💕" else "Set Mood ✨",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun NeoDownloadedSection(
    downloadedSongs: List<com.fenrir.her.db.entities.Song>,
    onSongClick: (Int) -> Unit,
    onViewAllClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Downloaded Music",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (downloadedSongs.isNotEmpty()) "${downloadedSongs.size} tracks • Offline ready" else "Listen without internet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (downloadedSongs.isNotEmpty()) {
                        Surface(
                            onClick = onPlayAllClick,
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Play",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onViewAllClick,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "View All",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (downloadedSongs.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(downloadedSongs.size) { index ->
                        val song = downloadedSongs[index]
                        Column(
                            modifier = Modifier
                                .width(112.dp)
                                .clickable { onSongClick(index) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                if (!song.song.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = song.song.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.album),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp).align(Alignment.Center),
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.70f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp),
                                ) {
                                    Text(
                                        text = "OFFLINE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = song.song.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = song.artists.joinToString { it.name }.ifBlank { "Unknown" },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No offline songs yet. Save your favorite tracks to listen seamlessly anytime, anywhere without mobile data 💕",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
