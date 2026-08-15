package com.idleskull.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.StrokeCap
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
 * skull rush -> slow themed fire burst -> cracked skull -> ink splash ->
 * heavy typewriter "DEFEATED" -> level-up card.
 *
 * This overlay is presentation-only. The timer and the next skull keep advancing behind it.
 */
@Composable
fun DefeatAnimationOverlay(
    darkMode: Boolean,
    eventId: Long,
    defeatedLevel: Int,
    nextLevel: Int,
    nextMaxHp: Long,
    onFinished: () -> Unit,
) {
    val timeline = remember(eventId) { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val background = MaterialTheme.colorScheme.background
    val foreground = MaterialTheme.colorScheme.onBackground
    val inkColor = if (darkMode) Color.White else Color.Black

    val accentColor = if (darkMode) Color(0xFFFF2B2B) else Color(0xFF22D94F)
    val middleFire = if (darkMode) Color(0xFFFF7A1A) else Color(0xFF7CFF43)
    val hotCore = if (darkMode) Color(0xFFFFF0A6) else Color(0xFFF2FFD4)
    val emberColor = if (darkMode) Color(0xFFFFB52E) else Color(0xFFD6FF45)
    val outerFire = if (darkMode) Color(0xFF9E1111) else Color(0xFF087A32)

    val crackColor = if (darkMode) Color.Black else Color.White
    val defeatedText = stringResource(R.string.copy_defeated)

    LaunchedEffect(eventId) {
        timeline.snapTo(0f)
        timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 14_800, easing = LinearEasing),
        )
        currentOnFinished()
    }

    val t = timeline.value
    val move = eased(phase(t, 0.00f, 0.09f))

    // The first black/white flood was intentionally removed. The first full-screen takeover
    // is now the themed fire burst itself, and it gets about 3.7 seconds to reach the corners.
    val explosion = eased(phase(t, 0.08f, 0.33f))
    val explosionFade = phase(t, 0.33f, 0.39f)

    val cleanStage = phase(t, 0.36f, 0.41f)
    val cracked = eased(phase(t, 0.38f, 0.49f))
    val splash = eased(phase(t, 0.45f, 0.54f))
    val splashFade = phase(t, 0.74f, 0.82f)

    // Roughly 3.55 seconds for eight letters: each hit has time to read as a separate impact.
    val typing = phase(t, 0.52f, 0.76f)
    val defeatedExit = phase(t, 0.78f, 0.84f)
    val skullExit = phase(t, 0.76f, 0.83f)

    val upgrade = eased(phase(t, 0.80f, 0.95f))
    val exit = phase(t, 0.96f, 1.00f)

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

            if (explosion > 0f && explosionFade < 1f) {
                drawFireBurst(
                    progress = explosion,
                    opacity = 1f - explosionFade,
                    outer = outerFire,
                    accent = accentColor,
                    middle = middleFire,
                    core = hotCore,
                    ember = emberColor,
                )
            }
        }

        if (move < 1f || t < 0.39f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = bottomToCenter * (1f - move))
                    .size(skullSize)
                    .graphicsLayer {
                        scaleX = 1f + 0.52f * move
                        scaleY = 1f + 0.52f * move
                        alpha = (1f - phase(t, 0.31f, 0.39f)).coerceIn(0f, 1f)
                    },
            ) {
                SkullBackdrop(
                    darkMode = darkMode,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 1f,
                )
            }
        }

        if (splash > 0f && splashFade < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                drawInkSplash(
                    progress = splash,
                    color = inkColor,
                    opacity = 1f - splashFade,
                )
            }
        }

        if (cracked > 0f && skullExit < 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(skullSize)
                    .graphicsLayer {
                        scaleX = 1.18f - 0.08f * cracked
                        scaleY = 1.18f - 0.08f * cracked
                        alpha = cracked * (1f - skullExit)
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

        if (visibleLetters > 0 && defeatedExit < 1f) {
            PixelText(
                text = defeatedText.take(visibleLetters),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 30.dp)
                    .graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                        translationX = textShake
                        alpha = 1f - defeatedExit
                    },
                color = if (darkMode) accentColor else foreground,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        if (upgrade > 0f) {
            UpgradeStage(
                defeatedLevel = defeatedLevel,
                nextLevel = nextLevel,
                nextMaxHp = nextMaxHp,
                progress = upgrade,
                accent = accentColor,
                foreground = foreground,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun UpgradeStage(
    defeatedLevel: Int,
    nextLevel: Int,
    nextMaxHp: Long,
    progress: Float,
    accent: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0f, 1f)
    val impact = (1f - p).coerceIn(0f, 1f)
    val nextScale = 1f + 0.28f * impact * impact

    Column(
        modifier = modifier
            .offset(y = 10.dp)
            .graphicsLayer { alpha = p },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelText(
            text = "Lv.$nextLevel",
            color = accent,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = nextScale
                scaleY = nextScale
            },
        )
        Spacer(Modifier.height(5.dp))
        PixelText(
            text = "HP $nextMaxHp",
            color = foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        PixelText(
            text = "↑",
            color = accent,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                scaleY = 0.65f + 0.35f * p
            },
        )
        Spacer(Modifier.height(8.dp))
        PixelText(
            text = "Lv.$defeatedLevel",
            color = foreground.copy(alpha = 0.62f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFireBurst(
    progress: Float,
    opacity: Float,
    outer: Color,
    accent: Color,
    middle: Color,
    core: Color,
    ember: Color,
) {
    val p = progress.coerceIn(0f, 1f)
    val a = opacity.coerceIn(0f, 1f)
    if (p <= 0f || a <= 0f) return

    val center = Offset(size.width * 0.50f, size.height * 0.50f)
    val maxRadius = hypot(size.width, size.height) * 0.58f

    fun flamePath(scale: Float, spikes: Int, wobble: Float): Path {
        val path = Path()
        for (i in 0 until spikes) {
            val angle = (2.0 * PI * i / spikes).toFloat()
            val deterministic = ((i * 37 + 11) % 17) / 16f
            val tooth = if (i % 3 == 0) 1.26f else if (i % 3 == 1) 0.88f else 1.08f
            val wave = 1f + wobble * sin(angle * 5f + deterministic * 2.7f)
            val radius = maxRadius * p * scale * tooth * wave * (0.88f + deterministic * 0.22f)
            val point = Offset(
                center.x + cos(angle) * radius,
                center.y + sin(angle) * radius,
            )
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        return path
    }

    // Dark outer flame, saturated themed body, warmer/lighter inner flame and a hot core.
    drawPath(flamePath(1.00f, 38, 0.10f), outer.copy(alpha = 0.76f * a))
    drawPath(flamePath(0.82f, 34, 0.13f), accent.copy(alpha = 0.92f * a))
    drawPath(flamePath(0.58f, 30, 0.16f), middle.copy(alpha = 0.94f * a))
    drawCircle(
        color = core.copy(alpha = 0.92f * a),
        radius = maxRadius * 0.24f * p,
        center = center,
    )

    // Long radial spark trails make the event read as fire/light scattering rather than paint.
    val sparkCount = 48
    for (i in 0 until sparkCount) {
        val angle = (2.0 * PI * i / sparkCount + ((i * 13) % 7) * 0.013).toFloat()
        val speed = 0.54f + ((i * 29) % 31) / 42f
        val distance = maxRadius * p * speed
        val tip = Offset(
            center.x + cos(angle) * distance,
            center.y + sin(angle) * distance,
        )
        val trailLength = maxRadius * (0.035f + (i % 6) * 0.009f) * (0.55f + p * 0.45f)
        val tail = Offset(
            tip.x - cos(angle) * trailLength,
            tip.y - sin(angle) * trailLength,
        )
        val sparkColor = when (i % 4) {
            0 -> core
            1 -> ember
            2 -> middle
            else -> accent
        }
        drawLine(
            color = sparkColor.copy(alpha = (0.48f + (i % 5) * 0.08f) * a),
            start = tail,
            end = tip,
            strokeWidth = (1.2.dp.toPx() + (i % 4) * 0.65.dp.toPx()),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = sparkColor.copy(alpha = 0.92f * a),
            radius = (1.7.dp.toPx() + (i % 5) * 0.75.dp.toPx()) * (0.65f + 0.35f * p),
            center = tip,
        )
    }

    // A second ring of small glowing fragments adds depth between the core and the long sparks.
    for (i in 0 until 24) {
        val angle = (2.0 * PI * i / 24 + 0.11 * (i % 3)).toFloat()
        val distance = maxRadius * p * (0.25f + ((i * 17) % 13) / 30f)
        val fragment = Offset(
            center.x + cos(angle) * distance,
            center.y + sin(angle) * distance,
        )
        drawCircle(
            color = if (i % 2 == 0) ember.copy(alpha = 0.68f * a) else core.copy(alpha = 0.58f * a),
            radius = (2.5.dp.toPx() + (i % 4) * 1.5.dp.toPx()) * p,
            center = fragment,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkSplash(
    progress: Float,
    color: Color,
    opacity: Float,
) {
    val p = progress.coerceIn(0f, 1f)
    val a = opacity.coerceIn(0f, 1f)
    if (p <= 0f || a <= 0f) return

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
    drawPath(blob, color.copy(alpha = 0.88f * a))

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
            color = color.copy(alpha = (0.72f + (i % 3) * 0.06f) * a),
            radius = radius,
            center = dropCenter,
        )
    }

    drawCircle(
        color = color.copy(alpha = 0.78f * a),
        radius = base * 0.62f,
        center = center + Offset(-base * 0.34f, base * 0.10f),
    )
    drawCircle(
        color = color.copy(alpha = 0.82f * a),
        radius = base * 0.54f,
        center = center + Offset(base * 0.30f, -base * 0.12f),
    )
}

private fun phase(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun eased(value: Float): Float = value * value * (3f - 2f * value)
