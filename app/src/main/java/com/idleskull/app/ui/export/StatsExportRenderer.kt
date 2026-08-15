package com.idleskull.app.ui.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.idleskull.app.R
import com.idleskull.app.model.ActivityType
import com.idleskull.app.model.TimeSession
import com.idleskull.app.ui.StatsEngine
import com.idleskull.app.ui.screens.StatsRange
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

enum class StatsExportStyle { MONOCHROME, COLOR }

data class StatsExportSpec(
    val range: StatsRange,
    val anchorDate: LocalDate,
    val activity: ActivityType,
    val sessions: List<TimeSession>,
    val darkMode: Boolean,
    val style: StatsExportStyle = StatsExportStyle.MONOCHROME,
) {
    fun fileName(): String {
        val key = when (range) {
            StatsRange.DAY -> anchorDate.toString()
            StatsRange.WEEK -> "week-${StatsEngine.weekDays(anchorDate).first()}"
            StatsRange.MONTH -> YearMonth.from(anchorDate).toString()
            StatsRange.YEAR -> anchorDate.year.toString()
        }
        val styleKey = if (style == StatsExportStyle.COLOR) "color" else "mono"
        val activityKey = if (activity == ActivityType.SLACK) "slack" else "grind"
        return "IdleSkull-$activityKey-${range.name.lowercase(Locale.ROOT)}-$key-$styleKey.png"
    }
}

object StatsExportRenderer {
    private const val WIDTH = 1080
    private const val BASE_HEIGHT = 1440
    private const val MARGIN = 76f

    fun writePng(context: Context, spec: StatsExportSpec, output: OutputStream) {
        val height = exportHeight(spec)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = Palette(spec.darkMode, spec.style)
        val typeface = loadPixelTypeface(context)
        val totals = StatsEngine.dailyTotals(spec.sessions, spec.activity)

        canvas.drawColor(palette.background)

        val paint = Paint().apply {
            isAntiAlias = false
            isDither = false
            isSubpixelText = false
            color = palette.ink
            this.typeface = typeface
        }

        drawText(canvas, paint, if (spec.activity == ActivityType.SLACK) "摆烂记录" else "开卷记录", MARGIN, 122f, 52f, palette.ink)
        drawRule(canvas, paint, 182f, palette.outline)

        val period = periodText(spec.range, spec.anchorDate)
        drawText(canvas, paint, period, MARGIN, 242f, 34f, palette.ink)

        val total = totalFor(spec.range, spec.anchorDate, totals)
        drawText(
            canvas,
            paint,
            "${if (spec.activity == ActivityType.SLACK) "总摆烂" else "总开卷"}  ${formatDuration(total)}",
            MARGIN,
            318f,
            58f,
            palette.totalSeverity(spec.range, total),
        )

        when (spec.range) {
            StatsRange.DAY -> drawDay(canvas, paint, palette, spec.sessions, spec.activity, spec.anchorDate, 404f)
            StatsRange.WEEK -> drawWeek(canvas, paint, palette, totals, spec.anchorDate, 414f)
            StatsRange.MONTH -> drawMonth(canvas, paint, palette, totals, YearMonth.from(spec.anchorDate), 402f)
            StatsRange.YEAR -> drawYear(canvas, paint, palette, totals, spec.anchorDate.year, 400f)
        }

        drawSkullSeal(context, canvas, spec.darkMode)

        val ruleY = height - 100f
        val footerY = height - 48f
        drawRule(canvas, paint, ruleY, palette.outline)
        drawText(canvas, paint, context.getString(R.string.copy_export_footer), MARGIN, footerY, 20f, palette.secondary)

        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            error("PNG 编码失败")
        }
        output.flush()
        bitmap.recycle()
    }

    private fun exportHeight(spec: StatsExportSpec): Int {
        if (spec.range != StatsRange.DAY) return BASE_HEIGHT
        val count = StatsEngine.sessionsOnDay(spec.sessions, spec.anchorDate, spec.activity).size
        return max(BASE_HEIGHT, 620 + count * 106)
    }

    private fun drawDay(
        canvas: Canvas,
        paint: Paint,
        palette: Palette,
        sessions: List<TimeSession>,
        activity: ActivityType,
        day: LocalDate,
        top: Float,
    ) {
        val rows = StatsEngine.sessionsOnDay(sessions, day, activity)
        drawText(canvas, paint, "当日记录", MARGIN, top, 28f, palette.ink)
        if (rows.isEmpty()) {
            drawPanel(canvas, paint, palette, MARGIN, top + 38f, WIDTH - MARGIN, top + 150f)
            drawText(canvas, paint, if (activity == ActivityType.SLACK) "这一天没有摆烂记录。" else "这一天没有开卷记录。", MARGIN + 30f, top + 108f, 26f, palette.secondary)
            return
        }

        val zone = ZoneId.systemDefault()
        var y = top + 48f
        rows.forEachIndexed { index, session ->
            val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
            val end = Instant.ofEpochMilli(session.endedAt).atZone(zone)
            drawPanel(canvas, paint, palette, MARGIN, y, WIDTH - MARGIN, y + 92f)
            if (palette.colourful) {
                paint.style = Paint.Style.FILL
                paint.color = palette.severityDaily(session.durationMs)
                canvas.drawRect(MARGIN, y, MARGIN + 8f, y + 92f, paint)
            }
            drawText(canvas, paint, session.name.ifBlank { "未命名" }, MARGIN + 26f, y + 38f, 26f, palette.ink)
            drawText(
                canvas,
                paint,
                formatDuration(session.durationMs),
                WIDTH - MARGIN - 26f,
                y + 38f,
                22f,
                palette.ink,
                Paint.Align.RIGHT,
            )
            val detail = "${start.format(DateTimeFormatter.ofPattern("HH:mm"))}-${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            drawText(canvas, paint, detail, MARGIN + 26f, y + 70f, 21f, palette.secondary)
            y += 106f
        }
    }

    private fun drawWeek(
        canvas: Canvas,
        paint: Paint,
        palette: Palette,
        totals: Map<LocalDate, Long>,
        anchor: LocalDate,
        top: Float,
    ) {
        val days = StatsEngine.weekDays(anchor)
        val values = days.map { totals[it] ?: 0L }
        val maxValue = max(values.maxOrNull() ?: 0L, 1L)
        drawText(canvas, paint, "7 日分布", MARGIN, top, 28f, palette.ink)
        val chartTop = top + 74f
        val chartBottom = 1060f
        val width = WIDTH - MARGIN * 2
        val gap = 18f
        val slotWidth = (width - gap * 6) / 7f
        val barWidth = slotWidth * 0.68f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = palette.outline
        canvas.drawLine(MARGIN, chartBottom, WIDTH - MARGIN, chartBottom, paint)
        listOf(0.25f, 0.5f, 0.75f).forEach { marker ->
            val y = chartBottom - 320f * marker
            paint.color = Color.argb(if (palette.colourful) 54 else 42, Color.red(palette.outline), Color.green(palette.outline), Color.blue(palette.outline))
            canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, paint)
        }

        days.forEachIndexed { index, day ->
            val value = values[index]
            val ratio = value.toFloat() / maxValue.toFloat()
            val slotLeft = MARGIN + index * (slotWidth + gap)
            val left = slotLeft + (slotWidth - barWidth) / 2f
            val bottom = chartBottom - 4f
            val height = if (value == 0L) 8f else 56f + 264f * ratio.coerceIn(0f, 1f)
            val topY = bottom - height
            paint.style = Paint.Style.FILL
            paint.color = if (value == 0L) palette.surface else palette.severityDaily(value)
            canvas.drawRect(left, topY, left + barWidth, bottom, paint)
            paint.color = if (value == 0L) palette.outline else lerpColor(palette.severityDaily(value), Color.WHITE, 0.18f)
            canvas.drawRect(left, topY, left + barWidth, topY + 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = palette.outline
            canvas.drawRect(left, topY, left + barWidth, bottom, paint)
            drawText(canvas, paint, weekday(day), slotLeft + slotWidth / 2f, chartBottom + 40f, 22f, palette.secondary, Paint.Align.CENTER)
            drawText(canvas, paint, formatDuration(value), slotLeft + slotWidth / 2f, chartBottom + 74f, 18f, palette.secondary, Paint.Align.CENTER)
        }
    }

    private fun drawMonth(
        canvas: Canvas,
        paint: Paint,
        palette: Palette,
        totals: Map<LocalDate, Long>,
        month: YearMonth,
        top: Float,
    ) {
        val days = StatsEngine.monthDays(month)
        val maxValue = max(days.maxOfOrNull { totals[it] ?: 0L } ?: 0L, 1L)
        drawText(canvas, paint, "月度像素热力图", MARGIN, top, 28f, palette.ink)
        val cellGap = 10f
        val cellWidth = (WIDTH - MARGIN * 2 - cellGap * 6) / 7f
        val cellHeight = 112f
        var y = top + 58f
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
            val left = MARGIN + index * (cellWidth + cellGap)
            drawText(canvas, paint, label, left + cellWidth / 2f, y + 24f, 18f, palette.secondary, Paint.Align.CENTER)
        }
        y += 40f
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(leading) { null } + days
        cells.chunked(7).forEach { week ->
            week.forEachIndexed { index, day ->
                if (day != null) {
                    val value = totals[day] ?: 0L
                    val ratio = value.toFloat() / maxValue.toFloat()
                    val shade = if (palette.colourful) {
                        if (value == 0L) palette.surface else palette.severityDaily(value)
                    } else {
                        lerpColor(palette.surface, palette.ink, if (value == 0L) 0.05f else 0.17f + 0.72f * ratio)
                    }
                    val left = MARGIN + index * (cellWidth + cellGap)
                    paint.style = Paint.Style.FILL
                    paint.color = shade
                    canvas.drawRect(left, y, left + cellWidth, y + cellHeight, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = palette.outline
                    canvas.drawRect(left, y, left + cellWidth, y + cellHeight, paint)
                    paint.style = Paint.Style.FILL
                    val textColor = if (contrastIsLight(shade)) Color.BLACK else Color.WHITE
                    drawText(canvas, paint, day.dayOfMonth.toString(), left + 12f, y + 34f, 20f, textColor)
                    if (value > 0L) drawText(canvas, paint, formatDuration(value), left + 12f, y + 78f, 16f, textColor)
                }
            }
            y += cellHeight + cellGap
        }
    }

    private fun drawYear(
        canvas: Canvas,
        paint: Paint,
        palette: Palette,
        totals: Map<LocalDate, Long>,
        year: Int,
        top: Float,
    ) {
        val months = StatsEngine.yearMonths(year)
        val values = months.map { month -> StatsEngine.monthDays(month).sumOf { totals[it] ?: 0L } }
        val maxValue = max(values.maxOrNull() ?: 0L, 1L)
        drawText(canvas, paint, "12 个月", MARGIN, top, 28f, palette.ink)
        var y = top + 48f
        months.forEachIndexed { index, month ->
            val value = values[index]
            drawText(canvas, paint, String.format(Locale.ROOT, "%02d", month.monthValue), MARGIN, y + 25f, 22f, palette.secondary)
            val left = MARGIN + 70f
            val right = WIDTH - MARGIN - 150f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = palette.outline
            canvas.drawRect(left, y, right, y + 36f, paint)
            paint.style = Paint.Style.FILL
            paint.color = palette.severityMonthlyTotal(value)
            val ratio = value.toFloat() / maxValue.toFloat()
            canvas.drawRect(left + 3f, y + 3f, left + 3f + (right - left - 6f) * ratio, y + 33f, paint)
            drawText(canvas, paint, formatDuration(value), WIDTH - MARGIN, y + 27f, 20f, palette.secondary, Paint.Align.RIGHT)
            y += 68f
        }
    }

    private fun totalFor(range: StatsRange, anchor: LocalDate, totals: Map<LocalDate, Long>): Long = when (range) {
        StatsRange.DAY -> totals[anchor] ?: 0L
        StatsRange.WEEK -> StatsEngine.weekDays(anchor).sumOf { totals[it] ?: 0L }
        StatsRange.MONTH -> StatsEngine.monthDays(YearMonth.from(anchor)).sumOf { totals[it] ?: 0L }
        StatsRange.YEAR -> StatsEngine.yearMonths(anchor.year).sumOf { month ->
            StatsEngine.monthDays(month).sumOf { totals[it] ?: 0L }
        }
    }

    private fun periodText(range: StatsRange, anchor: LocalDate): String = when (range) {
        StatsRange.DAY -> "日 · ${anchor.year}.${anchor.monthValue}.${anchor.dayOfMonth}"
        StatsRange.WEEK -> {
            val days = StatsEngine.weekDays(anchor)
            "周 · ${days.first().monthValue}.${days.first().dayOfMonth}-${days.last().monthValue}.${days.last().dayOfMonth}"
        }
        StatsRange.MONTH -> "月 · ${anchor.year}.${anchor.monthValue}"
        StatsRange.YEAR -> "年 · ${anchor.year}"
    }

    private fun drawPanel(canvas: Canvas, paint: Paint, palette: Palette, left: Float, top: Float, right: Float, bottom: Float) {
        paint.style = Paint.Style.FILL
        paint.color = palette.surface
        canvas.drawRect(left, top, right, bottom, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = palette.outline
        canvas.drawRect(left, top, right, bottom, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawRule(canvas: Canvas, paint: Paint, y: Float, color: Int) {
        paint.color = color
        paint.strokeWidth = 3f
        canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, paint)
    }

    private fun drawText(
        canvas: Canvas,
        paint: Paint,
        text: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        canvas.drawText(text, x, baseline, paint)
    }

    private fun drawSkullSeal(context: Context, canvas: Canvas, dark: Boolean) {
        val resource = if (dark) R.drawable.skull_backdrop_v2_dark else R.drawable.skull_backdrop_v2_light
        val source = BitmapFactory.decodeResource(context.resources, resource) ?: return
        val targetWidth = 560
        val ratio = targetWidth.toFloat() / source.width.toFloat()
        val targetHeight = (source.height * ratio).toInt()
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        val left = ((WIDTH - targetWidth) / 2f)
        val top = 238f
        val watermark = Paint().apply { alpha = if (dark) 38 else 32 }
        canvas.drawBitmap(scaled, left, top, watermark)
        if (scaled !== source) scaled.recycle()
        source.recycle()
    }

    private fun loadPixelTypeface(context: Context): Typeface {
        val id = context.resources.getIdentifier("fusion_pixel_12px_proportional", "font", context.packageName)
        return if (id != 0) runCatching { context.resources.getFont(id) }.getOrDefault(Typeface.DEFAULT) else Typeface.DEFAULT
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "${hours}h ${minutes}m ${seconds}s"
        } else {
            "${minutes}m ${seconds}s"
        }
    }

    private fun weekday(day: LocalDate): String = when (day.dayOfWeek.value) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val p = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * p).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * p).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * p).toInt(),
        )
    }

    private fun contrastIsLight(color: Int): Boolean =
        (Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114) > 150

    private class Palette(dark: Boolean, style: StatsExportStyle) {
        val colourful = style == StatsExportStyle.COLOR
        val background = if (dark) Color.rgb(12, 12, 12) else Color.rgb(246, 243, 235)
        val surface = if (dark) Color.rgb(24, 24, 24) else Color.rgb(255, 253, 247)
        val ink = if (dark) Color.rgb(242, 239, 231) else Color.rgb(14, 14, 14)
        val secondary = if (dark) Color.rgb(160, 160, 154) else Color.rgb(78, 78, 74)
        val outline = if (dark) Color.rgb(70, 70, 68) else Color.rgb(48, 48, 46)
        private val severityScale = if (dark) {
            intArrayOf(
                Color.rgb(77, 130, 88),
                Color.rgb(73, 107, 152),
                Color.rgb(169, 147, 62),
                Color.rgb(174, 107, 61),
                Color.rgb(152, 70, 70),
                Color.rgb(97, 45, 50),
                Color.rgb(112, 81, 127),
            )
        } else {
            intArrayOf(
                Color.rgb(77, 130, 88),
                Color.rgb(73, 107, 152),
                Color.rgb(169, 147, 62),
                Color.rgb(174, 107, 61),
                Color.rgb(152, 70, 70),
                Color.rgb(97, 45, 50),
                Color.rgb(112, 81, 127),
            )
        }

        fun accent(index: Int): Int = if (colourful) severityScale[index % severityScale.size] else ink

        fun totalSeverity(range: StatsRange, durationMs: Long): Int = when (range) {
            StatsRange.DAY -> severityDaily(durationMs)
            StatsRange.WEEK -> severityWeeklyTotal(durationMs)
            StatsRange.MONTH -> severityMonthlyTotal(durationMs)
            StatsRange.YEAR -> severityMonthlyTotal(durationMs / 12L)
        }

        fun severityDaily(durationMs: Long): Int {
            if (!colourful) return ink
            val minute = durationMs / 60_000L
            val index = when {
                minute < 30L -> 0
                minute < 60L -> 1
                minute < 120L -> 2
                minute < 240L -> 3
                minute < 360L -> 4
                minute < 480L -> 5
                else -> 6
            }
            return severityScale[index]
        }

        fun severityWeeklyTotal(durationMs: Long): Int {
            if (!colourful) return ink
            val hours = durationMs / 3_600_000L.toDouble()
            val index = when {
                hours < 3.0 -> 0
                hours < 7.0 -> 1
                hours < 14.0 -> 2
                hours < 22.0 -> 3
                hours < 31.0 -> 4
                hours < 40.0 -> 5
                else -> 6
            }
            return severityScale[index]
        }

        fun severityMonthlyTotal(durationMs: Long): Int {
            if (!colourful) return ink
            val hours = durationMs / 3_600_000L.toDouble()
            val index = when {
                hours < 12.0 -> 0
                hours < 28.0 -> 1
                hours < 48.0 -> 2
                hours < 72.0 -> 3
                hours < 100.0 -> 4
                hours < 132.0 -> 5
                else -> 6
            }
            return severityScale[index]
        }
    }
}

