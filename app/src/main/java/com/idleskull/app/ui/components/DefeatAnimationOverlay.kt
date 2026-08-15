package com.idleskull.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idleskull.app.R
import com.idleskull.app.model.SkullCatalog
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Full-screen boss defeat sequence.
 *
 * skull rush -> themed light explosion -> cracked skull ->
 * left-anchored heavy typewriter "DEFEATED" -> level-up card.
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

    val accentColor = if (darkMode) Color(0xFFFF3131) else Color(0xFF19C84B)
    val defeatedColor = if (darkMode) Color(0xFFFF3131) else Color(0xFF078A31)
    val innerLight = if (darkMode) Color(0xFFFF8A2A) else Color(0xFF8DFF67)
    val hotCore = if (darkMode) Color(0xFFFFF3C4) else Color(0xFFF4FFE7)
    val outerGlow = if (darkMode) Color(0xFF8F0012) else Color(0xFF087434)

    val crackColor = if (darkMode) Color.Black else Color.White
    val defeatedText = stringResource(R.string.copy_defeated)

    LaunchedEffect(eventId) {
        timeline.snapTo(0f)
        timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 16_800, easing = LinearEasing),
        )
        currentOnFinished()
    }

    val t = timeline.value
    val move = eased(phase(t, 0.00f, 0.08f))

    // The blast is deliberately slow: about 3.9 seconds from the center to the corners.
    // It is rendered as layered radial light, beams and glowing sparks, never as a solid blob.
    val explosion = eased(phase(t, 0.06f, 0.29f))
    val explosionFade = phase(t, 0.29f, 0.34f)

    val cleanStage = phase(t, 0.30f, 0.35f)
    val cracked = eased(phase(t, 0.34f, 0.43f))

    // About five seconds for the whole word. The word has a fixed left edge; only the
    // newest character gets the slam animation, so D never recenters as more letters appear.
    val typing = phase(t, 0.43f, 0.73f)
    val defeatedExit = phase(t, 0.75f, 0.80f)
    val skullExit = phase(t, 0.73f, 0.80f)

    val upgrade = eased(phase(t, 0.79f, 0.96f))
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
    val hit = if (visibleLetters == 0 || typing >= 1f) {
        0f
    } else {
        (1f - withinLetter).coerceIn(0f, 1f)
    }

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

        // Intact skull is the lowest visual layer. It rushes into place, then the light blast
        // explicitly renders above it and consumes it. It never stays on top of the explosion.
        if (move < 1f || t < 0.30f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = bottomToCenter * (1f - move))
                    .size(skullSize)
                    .graphicsLayer {
                        scaleX = 1f + 0.52f * move
                        scaleY = 1f + 0.52f * move
                        alpha = (1f - phase(t, 0.16f, 0.28f)).coerceIn(0f, 1f)
                    },
            ) {
                SkullBackdrop(
                    darkMode = darkMode,
                    level = defeatedLevel,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 1f,
                )
            }
        }

        // This canvas is intentionally after the intact skull in composition order, so the
        // expanding light covers the skull instead of being painted underneath it.
        Canvas(Modifier.fillMaxSize()) {
            if (cleanStage > 0f) {
                drawRect(background.copy(alpha = cleanStage.coerceIn(0f, 1f)))
            }

            if (explosion > 0f && explosionFade < 1f) {
                drawLightExplosion(
                    progress = explosion,
                    opacity = 1f - explosionFade,
                    outer = outerGlow,
                    accent = accentColor,
                    inner = innerLight,
                    core = hotCore,
                )
            }
        }

        // A cracked skull only comes back after the explosion has already taken over the screen.
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
                    level = defeatedLevel,
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
            DefeatedTypewriter(
                text = defeatedText,
                visibleLetters = visibleLetters,
                hit = hit,
                color = defeatedColor,
                alpha = 1f - defeatedExit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 56.dp),
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
private fun DefeatedTypewriter(
    text: String,
    visibleLetters: Int,
    hit: Float,
    color: Color,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val cellWidth = 33.dp
    val cellHeight = 66.dp

    Row(
        modifier = modifier
            .width(cellWidth * text.length.toFloat())
            .height(cellHeight)
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEachIndexed { index, char ->
            Box(
                modifier = Modifier
                    .width(cellWidth)
                    .height(cellHeight),
                contentAlignment = Alignment.Center,
            ) {
                if (index < visibleLetters) {
                    val newest = index == visibleLetters - 1
                    val impact = if (newest) hit * hit else 0f
                    PixelText(
                        text = char.toString(),
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = (-22f * impact).dp)
                            .graphicsLayer {
                                scaleX = 1f + 0.36f * impact
                                scaleY = 1f + 0.36f * impact
                                rotationZ = if (newest) {
                                    (if (index % 2 == 0) -1f else 1f) * 3.5f * impact
                                } else {
                                    0f
                                }
                            },
                        color = color,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
    val defeatedIdentity = SkullCatalog.identity(defeatedLevel)
    val nextIdentity = SkullCatalog.identity(nextLevel)
    val crossedTheme = SkullCatalog.isThemeTransition(defeatedLevel, nextLevel)

    Column(
        modifier = modifier
            .offset(y = 10.dp)
            .graphicsLayer { alpha = p },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelText(
            text = if (crossedTheme) {
                "${defeatedIdentity.theme.displayName} Boss 已被击败"
            } else {
                defeatedIdentity.title
            },
            color = if (crossedTheme) accent else foreground.copy(alpha = 0.72f),
            fontSize = if (crossedTheme) 17.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        PixelText(
            text = "Lv.$defeatedLevel",
            color = foreground.copy(alpha = 0.62f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        PixelText(
            text = "↓",
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
            text = nextIdentity.title,
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
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
            drawPath(
                path,
                accent.copy(alpha = 0.42f * progress),
                style = Stroke(width + 2.dp.toPx()),
            )
            drawPath(
                path,
                color.copy(alpha = 0.94f * progress),
                style = Stroke(width),
            )
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLightExplosion(
    progress: Float,
    opacity: Float,
    outer: Color,
    accent: Color,
    inner: Color,
    core: Color,
) {
    val p = progress.coerceIn(0f, 1f)
    val a = opacity.coerceIn(0f, 1f)
    if (p <= 0f || a <= 0f) return

    val center = Offset(size.width * 0.50f, size.height * 0.50f)
    val cornerRadius = hypot(size.width, size.height) * 0.54f
    val radius = cornerRadius * (0.035f + 0.98f * p)

    // Main bloom. The alpha drops continuously toward the rim, so this reads as emitted
    // light rather than a flat painted disk.
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to core.copy(alpha = 0.98f * a),
                0.08f to core.copy(alpha = 0.88f * a),
                0.20f to inner.copy(alpha = 0.78f * a),
                0.42f to accent.copy(alpha = 0.54f * a),
                0.68f to outer.copy(alpha = 0.26f * a),
                1.00f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )

    // Secondary softer halo gives the blast the same diffuse character as the eye glow.
    val haloRadius = radius * 0.72f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                core.copy(alpha = 0.58f * a),
                inner.copy(alpha = 0.34f * a),
                accent.copy(alpha = 0.12f * a),
                Color.Transparent,
            ),
            center = center,
            radius = haloRadius,
        ),
        radius = haloRadius,
        center = center,
    )

    // Expanding luminous ring: this is intentionally thin and translucent, not a filled body.
    val ringRadius = radius * (0.54f + 0.18f * p)
    drawCircle(
        color = core.copy(alpha = (0.24f + 0.26f * (1f - p)) * a),
        radius = ringRadius,
        center = center,
        style = Stroke(width = (2.2.dp.toPx() + 7.5.dp.toPx() * (1f - p))),
    )
    drawCircle(
        color = accent.copy(alpha = 0.18f * a),
        radius = ringRadius * 1.025f,
        center = center,
        style = Stroke(width = 1.4.dp.toPx()),
    )

    // Wide, faint rays make the explosion feel like light breaking outward from the core.
    val rayCount = 30
    for (i in 0 until rayCount) {
        val angle = (2.0 * PI * i / rayCount + ((i * 7) % 5) * 0.018).toFloat()
        val variation = 0.70f + ((i * 19) % 17) / 34f
        val rayLength = radius * variation
        val startRadius = radius * (0.06f + (i % 4) * 0.012f)
        val start = Offset(
            center.x + cos(angle) * startRadius,
            center.y + sin(angle) * startRadius,
        )
        val end = Offset(
            center.x + cos(angle) * rayLength,
            center.y + sin(angle) * rayLength,
        )
        val rayColor = when (i % 3) {
            0 -> core
            1 -> inner
            else -> accent
        }
        drawLine(
            color = rayColor.copy(alpha = (0.07f + (i % 5) * 0.018f) * a),
            start = start,
            end = end,
            strokeWidth = (3.0.dp.toPx() + (i % 4) * 2.1.dp.toPx()) * (0.62f + 0.38f * p),
            cap = StrokeCap.Round,
        )
    }

    // A few sharper streaks and soft spark glows create the "firelight scattering" impression
    // without turning the blast into opaque orange/green geometry.
    val sparkCount = 34
    for (i in 0 until sparkCount) {
        val angle = (2.0 * PI * i / sparkCount + ((i * 11) % 9) * 0.014).toFloat()
        val distance = radius * (0.38f + ((i * 23) % 29) / 45f)
        val tip = Offset(
            center.x + cos(angle) * distance,
            center.y + sin(angle) * distance,
        )
        val trail = radius * (0.035f + (i % 5) * 0.008f)
        val tail = Offset(
            tip.x - cos(angle) * trail,
            tip.y - sin(angle) * trail,
        )
        val sparkColor = when (i % 4) {
            0 -> core
            1 -> inner
            2 -> accent
            else -> core
        }
        drawLine(
            color = sparkColor.copy(alpha = (0.28f + (i % 4) * 0.05f) * a),
            start = tail,
            end = tip,
            strokeWidth = (1.0.dp.toPx() + (i % 3) * 0.55.dp.toPx()),
            cap = StrokeCap.Round,
        )

        val glowRadius = (4.5.dp.toPx() + (i % 4) * 2.0.dp.toPx()) * (0.7f + 0.3f * p)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    sparkColor.copy(alpha = 0.72f * a),
                    sparkColor.copy(alpha = 0.22f * a),
                    Color.Transparent,
                ),
                center = tip,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = tip,
        )
    }

    // Brief cross-flare through the hottest center, similar to the existing eye-light language.
    val flare = radius * (0.18f + 0.20f * p)
    drawLine(
        color = core.copy(alpha = 0.34f * a),
        start = Offset(center.x - flare, center.y),
        end = Offset(center.x + flare, center.y),
        strokeWidth = 1.6.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = core.copy(alpha = 0.28f * a),
        start = Offset(center.x, center.y - flare * 0.72f),
        end = Offset(center.x, center.y + flare * 0.72f),
        strokeWidth = 1.4.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun phase(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun eased(value: Float): Float = value * value * (3f - 2f * value)
