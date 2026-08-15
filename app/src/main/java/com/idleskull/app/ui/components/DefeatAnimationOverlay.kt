package com.idleskull.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idleskull.app.R
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Full-screen boss defeat sequence.
 *
 * The animation is deliberately rendered from deterministic vector/Compose primitives so
 * no generated bitmap frames are needed: skull rush -> ink flood -> slow color burst ->
 * cracked skull -> ink splash -> typewriter "DEFEATED".
 */
@Composable
fun DefeatAnimationOverlay(
    darkMode: Boolean,
    eventId: Long,
    onFinished: () -> Unit,
) {
    val timeline = remember(eventId) { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val background = MaterialTheme.colorScheme.background
    val foreground = MaterialTheme.colorScheme.onBackground
    val inkColor = if (darkMode) Color.White else Color.Black
    val accentColor = if (darkMode) Color(0xFFFF2B2B) else Color(0xFF22D94F)
    val crackColor = if (darkMode) Color.Black else Color.White
    val defeatedText = stringResource(R.string.copy_defeated)

    LaunchedEffect(eventId) {
        timeline.snapTo(0f)
        timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 8_800, easing = LinearEasing),
        )
        currentOnFinished()
    }

    val t = timeline.value
    val move = eased(phase(t, 0.00f, 0.10f))
    val ink = eased(phase(t, 0.05f, 0.24f))
    val inkFade = phase(t, 0.22f, 0.30f)

    // Roughly 3.25 seconds of the 8.8 second sequence. The themed burst now visibly grows
    // all the way from the center to beyond the screen corners before it starts fading.
    val explosion = eased(phase(t, 0.20f, 0.57f))
    val explosionFade = phase(t, 0.57f, 0.65f)

    val cleanStage = phase(t, 0.62f, 0.69f)
    val cracked = eased(phase(t, 0.61f, 0.72f))
    val splash = eased(phase(t, 0.70f, 0.80f))
    val typing = phase(t, 0.76f, 0.94f)
    val exit = phase(t, 0.95f, 1.00f)

    val typedPosition = typing * defeatedText.length
    val visibleLetters = if (typing <= 0f) {
        0
    } else {
        ceil(typedPosition.toDouble()).toInt().coerceIn(0, defeatedText.length)
    }
    val withinLetter = if (typing <= 0f || typing >= 1f) {
        1f
    } else {
        typedPosition - floor(typedPosition)
    }
    val hit = if (visibleLetters == 0 || typing >= 1f) 0f else (1f - withinLetter).coerceIn(0f, 1f)
    val textScale = 1f + 0.34f * hit * hit
    val textShake = (if (visibleLetters % 2 == 0) -1f else 1f) * 9f * hit

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit }
            .pointerInput(eventId) {
                // The defeat cut-in is intentionally modal for its short lifetime.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val skullSize = 310.dp
        val bottomToCenter = (maxHeight / 2f - skullSize / 2f).coerceAtLeast(0.dp)

        Canvas(Modifier.fillMaxSize()) {
            if (cleanStage > 0f) {
                drawRect(background.copy(alpha = cleanStage.coerceIn(0f, 1f)))
            }

            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = hypot(size.width, size.height) * 0.58f

            if (ink > 0f && inkFade < 1f) {
                drawCircle(
                    color = inkColor.copy(alpha = 1f - inkFade),
                    radius = maxRadius * ink,
                    center = center,
                )
            }

            if (explosion > 0f && explosionFade < 1f) {
                val fade = 1f - explosionFade
                val grow = explosion.coerceIn(0f, 1f)
                drawCircle(
                    color = accentColor.copy(alpha = 0.94f * fade),
                    radius = maxRadius * grow,
                    center = center,
                )

                // Keep the burst feeling explosive without making the main expansion itself fast.
                // Rays lengthen with the same 3+ second growth, while their tips are intentionally
                // uneven so the result is less like a perfect loading circle.
                val rayCount = 20
                for (i in 0 until rayCount) {
                    val angle = (2.0 * PI * i / rayCount).toFloat()
                    val variance = 0.72f + ((i * 37) % 11) / 20f
                    val innerRadius = maxRadius * 0.07f * grow
                    val outerRadius = maxRadius * variance * grow
                    val half = 0.030f + (i % 4) * 0.007f
                    val path = Path().apply {
                        moveTo(
                            center.x + cos(angle - half) * innerRadius,
                            center.y + sin(angle - half) * innerRadius,
                        )
                        lineTo(
                            center.x + cos(angle) * outerRadius,
                            center.y + sin(angle) * outerRadius,
                        )
                        lineTo(
                            center.x + cos(angle + half) * innerRadius,
                            center.y + sin(angle + half) * innerRadius,
                        )
                        close()
                    }
                    drawPath(path, accentColor.copy(alpha = 0.62f * fade))
                }
            }
        }

        if (move < 1f || t < 0.30f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = bottomToCenter * (1f - move))
                    .size(skullSize)
                    .graphicsLayer {
                        scaleX = 1f + 0.52f * move
                        scaleY = 1f + 0.52f * move
                        alpha = (1f - phase(t, 0.22f, 0.30f)).coerceIn(0f, 1f)
                    },
            ) {
                SkullBackdrop(
                    darkMode = darkMode,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 1f,
                )
            }
        }

        if (splash > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                drawInkSplash(
                    progress = splash,
                    color = inkColor,
                )
            }
        }

        if (cracked > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(skullSize)
                    .graphicsLayer {
                        scaleX = 1.18f - 0.08f * cracked
                        scaleY = 1.18f - 0.08f * cracked
                        alpha = cracked
                    },
            ) {
                SkullBackdrop(
                    darkMode = darkMode,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 1f,
                )
                CrackOverlay(
                    color = crackColor,
                    accent = accentColor,
                    progress = cracked,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (visibleLetters > 0) {
            PixelText(
                text = defeatedText.take(visibleLetters),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                        translationX = textShake
                    },
                color = if (darkMode) accentColor else foreground,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CrackOverlay(
    color: Color,
    accent: Color,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        fun crack(points: List<Offset>, width: Float) {
            if (points.size < 2) return
            val path = Path().apply {
                moveTo(points.first().x * size.width, points.first().y * size.height)
                points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
            }
            drawPath(path, accent.copy(alpha = 0.42f * progress), style = androidx.compose.ui.graphics.drawscope.Stroke(width + 2.dp.toPx()))
            drawPath(path, color.copy(alpha = 0.94f * progress), style = androidx.compose.ui.graphics.drawscope.Stroke(width))
        }

        val w = 2.2.dp.toPx()
        crack(
            listOf(
                Offset(0.50f, 0.15f), Offset(0.47f, 0.24f), Offset(0.53f, 0.31f),
                Offset(0.48f, 0.39f), Offset(0.52f, 0.47f),
            ),
            w,
        )
        crack(
            listOf(
                Offset(0.48f, 0.27f), Offset(0.39f, 0.31f), Offset(0.35f, 0.39f), Offset(0.29f, 0.43f),
            ),
            w * 0.78f,
        )
        crack(
            listOf(
                Offset(0.52f, 0.33f), Offset(0.61f, 0.36f), Offset(0.65f, 0.43f), Offset(0.72f, 0.47f),
            ),
            w * 0.78f,
        )
        crack(
            listOf(
                Offset(0.50f, 0.45f), Offset(0.44f, 0.52f), Offset(0.47f, 0.60f), Offset(0.42f, 0.67f),
            ),
            w * 0.66f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkSplash(
    progress: Float,
    color: Color,
) {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return

    val center = Offset(size.width * 0.50f, size.height * 0.55f)
    val base = min(size.width, size.height) * 0.23f * p
    val points = 30
    val blob = Path()

    for (i in 0 until points) {
        val angle = (2.0 * PI * i / points).toFloat()
        val jagged = when (i % 7) {
            0 -> 1.52f
            1 -> 0.82f
            2 -> 1.12f
            3 -> 0.72f
            4 -> 1.30f
            5 -> 0.92f
            else -> 1.06f
        }
        val wave = 1f + 0.10f * sin(angle * 5f + 0.8f)
        val radius = base * jagged * wave
        val point = Offset(
            x = center.x + cos(angle) * radius,
            y = center.y + sin(angle) * radius * 0.72f,
        )
        if (i == 0) blob.moveTo(point.x, point.y) else blob.lineTo(point.x, point.y)
    }
    blob.close()
    drawPath(blob, color.copy(alpha = 0.88f))

    // Satellite drops make the shape read as thrown ink rather than a decorative brush stroke.
    val dropAngles = floatArrayOf(-2.85f, -2.35f, -1.92f, -1.35f, -0.72f, -0.20f, 0.36f, 0.94f, 1.42f, 2.05f, 2.58f)
    for (i in dropAngles.indices) {
        val angle = dropAngles[i]
        val distance = base * (1.35f + (i % 4) * 0.22f)
        val dropCenter = Offset(
            x = center.x + cos(angle) * distance,
            y = center.y + sin(angle) * distance * 0.78f,
        )
        val radius = (3.5.dp.toPx() + (i % 5) * 2.2.dp.toPx()) * p
        drawCircle(
            color = color.copy(alpha = 0.72f + (i % 3) * 0.06f),
            radius = radius,
            center = dropCenter,
        )
    }

    // A few denser lobes keep the middle from looking like a simple star polygon.
    drawCircle(
        color = color.copy(alpha = 0.78f),
        radius = base * 0.62f,
        center = center + Offset(-base * 0.34f, base * 0.10f),
    )
    drawCircle(
        color = color.copy(alpha = 0.82f),
        radius = base * 0.54f,
        center = center + Offset(base * 0.30f, -base * 0.12f),
    )
}

private fun phase(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun eased(value: Float): Float = value * value * (3f - 2f * value)
