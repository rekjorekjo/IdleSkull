package com.idleskull.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.idleskull.app.R
import com.idleskull.app.model.SkullCatalog
import com.idleskull.app.model.SkullTheme
import kotlin.math.ceil
import kotlin.math.min

object PixelCutShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val step = with(density) { 6.dp.toPx() }.coerceAtMost(min(size.width, size.height) / 3f)
        val path = Path().apply {
            moveTo(step, 0f)
            lineTo(size.width - step, 0f)
            lineTo(size.width - step, step)
            lineTo(size.width, step)
            lineTo(size.width, size.height - step)
            lineTo(size.width - step, size.height - step)
            lineTo(size.width - step, size.height)
            lineTo(step, size.height)
            lineTo(step, size.height - step)
            lineTo(0f, size.height - step)
            lineTo(0f, step)
            lineTo(step, step)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun SkullBackdrop(
    darkMode: Boolean,
    level: Int? = null,
    modifier: Modifier = Modifier,
    alpha: Float = 0.34f,
) {
    val identity = level?.let(SkullCatalog::identity)
    val drawable = when (identity?.theme) {
        SkullTheme.BABY -> R.drawable.skull_theme_baby
        SkullTheme.CLOWN -> R.drawable.skull_theme_clown
        SkullTheme.DANCE_KING -> R.drawable.skull_theme_dance_king
        SkullTheme.CHEF -> R.drawable.skull_theme_chef
        SkullTheme.SAMURAI -> R.drawable.skull_theme_samurai
        SkullTheme.PIRATE -> R.drawable.skull_theme_pirate
        SkullTheme.SERPENT_KING -> R.drawable.skull_theme_serpent_king
        SkullTheme.DEMON -> R.drawable.skull_theme_demon
        null -> if (darkMode) R.drawable.skull_backdrop_v2_dark else R.drawable.skull_backdrop_v2_light
    }

    // Level-bound theme art is stored once as a white/alpha mask. Tinting keeps the same PNG
    // usable in dark and light mode, so eight skull themes require eight files rather than
    // sixteen. Callers without a level (for example About) keep the original brand artwork.
    androidx.compose.foundation.Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = modifier.alpha(alpha),
        contentScale = ContentScale.Fit,
        colorFilter = if (identity == null) {
            null
        } else {
            ColorFilter.tint(if (darkMode) Color.White else Color.Black)
        },
    )
}

/**
 * Pixel text backed by Fusion Pixel Font when the generated font resource is present.
 *
 * The old implementation deliberately rasterised system monospace text at a very low
 * resolution and enlarged the bitmap. That made dense Chinese strokes merge together.
 * Fusion Pixel is already a real pixel font, so it is drawn directly instead.
 */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { fontSize.toPx() }
    val colorArgb = color.toArgb()
    val bold = fontWeight.weight >= FontWeight.SemiBold.weight

    AndroidView(
        modifier = modifier,
        factory = { context -> PixelTextView(context) },
        update = { view ->
            view.setPixelText(
                text = text,
                color = colorArgb,
                textSizePx = sizePx,
                bold = bold,
                alignment = textAlign,
            )
        },
    )
}


@Composable
fun PixelParagraph(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 13.sp,
    textAlign: TextAlign = TextAlign.Start,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { fontSize.toPx() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                includeFontPadding = false
                setLineSpacing(0f, 1.38f)
            }
        },
        update = { view ->
            val fontId = view.resources.getIdentifier(
                "fusion_pixel_12px_proportional",
                "font",
                view.context.packageName,
            )
            view.typeface = if (fontId != 0) {
                runCatching { view.resources.getFont(fontId) }.getOrDefault(Typeface.DEFAULT)
            } else {
                Typeface.DEFAULT
            }
            view.text = text
            view.setTextColor(color.toArgb())
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizePx)
            view.gravity = when (textAlign) {
                TextAlign.Center -> Gravity.CENTER_HORIZONTAL
                TextAlign.End, TextAlign.Right -> Gravity.END
                else -> Gravity.START
            }
        },
    )
}

@Composable
fun TimerPixelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 42.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { fontSize.toPx() }
    AndroidView(
        modifier = modifier,
        factory = { context -> LegacyTimerTextView(context) },
        update = { view ->
            view.setTimerText(
                text = text,
                color = color.toArgb(),
                textSizePx = sizePx,
                bold = fontWeight.weight >= FontWeight.SemiBold.weight,
                alignment = textAlign,
            )
        },
    )
}


@Composable
fun PixelInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    textAlign: TextAlign = TextAlign.Center,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bgColor = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(PixelCutShape)
            .background(bgColor, PixelCutShape)
            .border(2.dp, outline, PixelCutShape),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                PixelEditText(context).apply {
                    setPadding(12.dpToPx(context), 0, 12.dpToPx(context), 0)
                    this.background = null
                    includeFontPadding = false
                    isSingleLine = true
                    inputType = if (numeric) {
                        InputType.TYPE_CLASS_NUMBER
                    } else {
                        InputType.TYPE_CLASS_TEXT
                    }
                }
            },
            update = { view ->
                view.onValueChanged = onValueChange
                view.setPixelStyle(
                    color = textColor,
                    alignment = textAlign,
                )
                view.setValue(value)
            },
        )
    }
}

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inverted: Boolean = false,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
) {
    val fg = if (inverted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    val bg = if (inverted) Color.Transparent else MaterialTheme.colorScheme.primary
    val border = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(PixelCutShape)
            .background(bg, PixelCutShape)
            .border(2.dp, border, PixelCutShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(
            text = text,
            color = if (enabled) fg else fg.copy(alpha = 0.35f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PixelChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        inverted = !selected,
        padding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    )
}

@Composable
fun PixelPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(PixelCutShape)
            .background(MaterialTheme.colorScheme.surface, PixelCutShape)
            .border(2.dp, MaterialTheme.colorScheme.outline, PixelCutShape)
            .padding(14.dp),
    ) { content() }
}


private class PixelEditText(context: Context) : EditText(context) {
    var onValueChanged: (String) -> Unit = {}
    private var internalChange = false

    private val pixelTypeface: Typeface by lazy {
        val fontId = resources.getIdentifier(
            "fusion_pixel_12px_proportional",
            "font",
            context.packageName,
        )
        if (fontId != 0) {
            runCatching { resources.getFont(fontId) }.getOrDefault(Typeface.DEFAULT)
        } else {
            Typeface.DEFAULT
        }
    }

    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!internalChange) onValueChanged(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    fun setPixelStyle(color: Int, alignment: TextAlign) {
        typeface = pixelTypeface
        setTextColor(color)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = when (alignment) {
            TextAlign.Center -> Gravity.CENTER
            TextAlign.End, TextAlign.Right -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    fun setValue(value: String) {
        if (text?.toString() == value) return
        internalChange = true
        setText(value)
        setSelection(text?.length ?: 0)
        internalChange = false
    }
}

private fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()

private class PixelTextView(context: Context) : View(context) {
    private val pixelTypeface: Typeface by lazy {
        val fontId = resources.getIdentifier(
            "fusion_pixel_12px_proportional",
            "font",
            context.packageName,
        )
        if (fontId != 0) {
            runCatching { resources.getFont(fontId) }.getOrDefault(Typeface.DEFAULT)
        } else {
            Typeface.DEFAULT
        }
    }

    private val paint = Paint().apply {
        isAntiAlias = false
        isDither = false
        isSubpixelText = false
        isLinearText = false
    }

    private var value: String = ""
    private var alignment: TextAlign = TextAlign.Start
    private var wantsBold: Boolean = false

    fun setPixelText(
        text: String,
        color: Int,
        textSizePx: Float,
        bold: Boolean,
        alignment: TextAlign,
    ) {
        var changed = false
        if (value != text) {
            value = text
            changed = true
        }
        if (paint.color != color) {
            paint.color = color
            changed = true
        }
        if (paint.textSize != textSizePx) {
            paint.textSize = textSizePx
            changed = true
        }
        if (wantsBold != bold) {
            wantsBold = bold
            changed = true
        }
        if (this.alignment != alignment) {
            this.alignment = alignment
            changed = true
        }

        paint.typeface = pixelTypeface
        // Android's fake bold expands every stroke. On small Han glyphs that can merge
        // neighbouring pixels, so use it only for Latin/numeric text.
        paint.isFakeBoldText = wantsBold && value.all { it.code < 0x2E80 }
        contentDescription = text

        if (changed) {
            requestLayout()
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        paint.typeface = pixelTypeface
        val metrics = paint.fontMetrics
        val desiredWidth = ceil(paint.measureText(value).toDouble()).toInt() + paddingLeft + paddingRight
        val desiredHeight = ceil((metrics.bottom - metrics.top).toDouble()).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth.coerceAtLeast(suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(desiredHeight.coerceAtLeast(suggestedMinimumHeight), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        paint.typeface = pixelTypeface
        paint.textAlign = when (alignment) {
            TextAlign.Center -> Paint.Align.CENTER
            TextAlign.End, TextAlign.Right -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }

        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        val metrics = paint.fontMetrics
        val textHeight = metrics.bottom - metrics.top
        val baseline = paddingTop + (availableHeight - textHeight) / 2f - metrics.top
        val x = when (alignment) {
            TextAlign.Center -> width / 2f
            TextAlign.End, TextAlign.Right -> width - paddingRight.toFloat()
            else -> paddingLeft.toFloat()
        }
        canvas.drawText(value, x, baseline, paint)
    }
}


/** Keeps the pre-0.2.3 timer-number renderer that the main clock used before. */
private class LegacyTimerTextView(context: Context) : View(context) {
    private val paint = Paint().apply {
        isAntiAlias = false
        isDither = false
        isSubpixelText = false
        isLinearText = false
        typeface = Typeface.MONOSPACE
    }
    private val bitmapPaint = Paint().apply {
        isAntiAlias = false
        isDither = false
        isFilterBitmap = false
    }
    private var value = ""
    private var alignment: TextAlign = TextAlign.Center
    private var cachedBitmap: Bitmap? = null
    private var bitmapDirty = true

    fun setTimerText(
        text: String,
        color: Int,
        textSizePx: Float,
        bold: Boolean,
        alignment: TextAlign,
    ) {
        var changed = false
        if (value != text) { value = text; changed = true }
        if (paint.color != color) { paint.color = color; changed = true }
        if (paint.textSize != textSizePx) { paint.textSize = textSizePx; changed = true }
        if (paint.isFakeBoldText != bold) { paint.isFakeBoldText = bold; changed = true }
        if (this.alignment != alignment) { this.alignment = alignment; changed = true }
        contentDescription = text
        if (changed) { bitmapDirty = true; requestLayout(); invalidate() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = paint.fontMetrics
        val desiredWidth = ceil(paint.measureText(value).toDouble()).toInt() + paddingLeft + paddingRight
        val desiredHeight = ceil((metrics.bottom - metrics.top).toDouble()).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth.coerceAtLeast(suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(desiredHeight.coerceAtLeast(suggestedMinimumHeight), heightMeasureSpec),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) bitmapDirty = true
    }

    override fun onDetachedFromWindow() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (bitmapDirty || cachedBitmap == null) rebuildBitmap()
        cachedBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), bitmapPaint)
        }
    }

    private fun rebuildBitmap() {
        cachedBitmap?.recycle()
        val pixelScale = (resources.displayMetrics.density * 1.35f).toInt().coerceIn(2, 4)
        val bitmapWidth = ((width + pixelScale - 1) / pixelScale).coerceAtLeast(1)
        val bitmapHeight = ((height + pixelScale - 1) / pixelScale).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val lowCanvas = android.graphics.Canvas(bitmap)
        val lowPaint = Paint(paint).apply {
            textSize = paint.textSize / pixelScale
            isAntiAlias = false
            isDither = false
            isSubpixelText = false
            isLinearText = false
        }
        val metrics = lowPaint.fontMetrics
        val y = bitmapHeight / 2f - (metrics.ascent + metrics.descent) / 2f
        val x = when (alignment) {
            TextAlign.Center -> bitmapWidth / 2f
            TextAlign.End, TextAlign.Right -> bitmapWidth.toFloat()
            else -> 0f
        }
        lowPaint.textAlign = when (alignment) {
            TextAlign.Center -> Paint.Align.CENTER
            TextAlign.End, TextAlign.Right -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        lowCanvas.drawText(value, x, y, lowPaint)
        cachedBitmap = bitmap
        bitmapDirty = false
    }
}
