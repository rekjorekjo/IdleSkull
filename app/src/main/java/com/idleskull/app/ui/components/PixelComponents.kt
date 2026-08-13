package com.idleskull.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier,
    alpha: Float = if (darkMode) 0.17f else 0.10f,
) {
    androidx.compose.foundation.Image(
        painter = painterResource(
            if (darkMode) R.drawable.skull_backdrop_dark else R.drawable.skull_backdrop_light,
        ),
        contentDescription = null,
        modifier = modifier.alpha(alpha),
        contentScale = ContentScale.Fit,
    )
}

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

private class PixelTextView(context: Context) : View(context) {
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

    private var value: String = ""
    private var alignment: TextAlign = TextAlign.Start
    private var cachedBitmap: Bitmap? = null
    private var bitmapDirty = true

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
        if (paint.isFakeBoldText != bold) {
            paint.isFakeBoldText = bold
            changed = true
        }
        if (this.alignment != alignment) {
            this.alignment = alignment
            changed = true
        }
        contentDescription = text
        if (changed) {
            bitmapDirty = true
            requestLayout()
            invalidate()
        }
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
            canvas.drawBitmap(
                bitmap,
                null,
                Rect(0, 0, width, height),
                bitmapPaint,
            )
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
        val y = paddingTop / pixelScale.toFloat() - metrics.top
        val x = when (alignment) {
            TextAlign.Center -> bitmapWidth / 2f
            TextAlign.End, TextAlign.Right -> bitmapWidth - paddingRight / pixelScale.toFloat()
            else -> paddingLeft / pixelScale.toFloat()
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
