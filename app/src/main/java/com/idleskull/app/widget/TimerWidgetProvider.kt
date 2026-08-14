package com.idleskull.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.idleskull.app.MainActivity
import com.idleskull.app.R
import com.idleskull.app.data.TimerRepository
import com.idleskull.app.model.EndReason
import com.idleskull.app.model.TimerMode
import com.idleskull.app.model.TimerStatus
import kotlin.math.ceil

class TimerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        appWidgetManager.updateAppWidget(appWidgetId, buildViews(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val repo = TimerRepository(context)
        when (intent.action) {
            ACTION_TOGGLE -> {
                repo.ensureCountdownComplete()
                val active = repo.loadActive()
                if (active == null) repo.startCountUp() else repo.pauseOrResume()
                refreshAll(context)
            }
            ACTION_STOP -> {
                repo.finish(EndReason.MANUAL)
                refreshAll(context)
            }
            ACTION_COUNTDOWN_FINISH -> {
                repo.ensureCountdownComplete()
                refreshAll(context)
            }
            ACTION_FORMAT_BOUNDARY -> refreshAll(context)
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val repo = TimerRepository(context)
        repo.ensureCountdownComplete()
        val active = repo.loadActive()
        val dark = repo.isDarkMode()
        val colors = WidgetColors(dark)
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        applyTheme(views, dark, colors)
        views.setImageViewBitmap(
            R.id.widget_title,
            renderPixelText(context, context.getString(R.string.copy_widget_title), 13f, colors.main, bold = true),
        )

        val openApp = PendingIntent.getActivity(
            context,
            31,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_title_row, openApp)

        val toggle = PendingIntent.getBroadcast(
            context,
            32,
            Intent(context, TimerWidgetProvider::class.java)
                .setAction(ACTION_TOGGLE)
                .setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context,
            33,
            Intent(context, TimerWidgetProvider::class.java)
                .setAction(ACTION_STOP)
                .setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_toggle, toggle)
        views.setOnClickPendingIntent(R.id.widget_stop, stop)

        if (active == null) {
            setPixelLabel(views, context, R.id.widget_status, context.getString(R.string.copy_widget_waiting), 10f, colors.secondary)
            setPixelLabel(views, context, R.id.widget_toggle, context.getString(R.string.copy_widget_start), 12f, colors.primaryText, true)
            views.setViewVisibility(R.id.widget_stop, View.GONE)
            views.setViewVisibility(R.id.widget_chronometer, View.GONE)
            views.setViewVisibility(R.id.widget_static_time, View.VISIBLE)
            views.setTextViewText(R.id.widget_static_time, "00:00:00")
            views.setTextColor(R.id.widget_static_time, colors.main)
        } else {
            val display = active.displayMsAt(nowWall)
            val status = when {
                active.status == TimerStatus.PAUSED -> context.getString(R.string.copy_widget_paused)
                active.mode == TimerMode.COUNT_DOWN -> context.getString(R.string.copy_widget_countdown_running)
                else -> context.getString(R.string.copy_widget_running)
            }
            setPixelLabel(views, context, R.id.widget_status, status, 10f, colors.secondary)
            setPixelLabel(
                views,
                context,
                R.id.widget_toggle,
                if (active.status == TimerStatus.RUNNING) context.getString(R.string.copy_pause) else context.getString(R.string.copy_resume),
                12f,
                colors.primaryText,
                true,
            )
            setPixelLabel(views, context, R.id.widget_stop, context.getString(R.string.copy_widget_stop), 12f, colors.main, true)
            views.setViewVisibility(R.id.widget_stop, View.VISIBLE)

            if (active.status == TimerStatus.RUNNING) {
                views.setViewVisibility(R.id.widget_static_time, View.GONE)
                views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
                val format = widgetChronometerFormat(display)
                if (active.mode == TimerMode.COUNT_DOWN) {
                    views.setChronometerCountDown(R.id.widget_chronometer, true)
                    views.setChronometer(R.id.widget_chronometer, nowElapsed + display, format, true)
                } else {
                    views.setChronometerCountDown(R.id.widget_chronometer, false)
                    views.setChronometer(R.id.widget_chronometer, nowElapsed - display, format, true)
                }
            } else {
                views.setViewVisibility(R.id.widget_chronometer, View.GONE)
                views.setViewVisibility(R.id.widget_static_time, View.VISIBLE)
                views.setTextViewText(R.id.widget_static_time, formatWidgetTime(display))
                views.setTextColor(R.id.widget_static_time, colors.main)
            }
        }
        updateCountdownCompletionAlarm(context, active, active?.displayMsAt(nowWall) ?: 0L)
        updateFormatBoundaryAlarm(context, active, active?.displayMsAt(nowWall) ?: 0L, nowWall)
        return views
    }

    private fun applyTheme(views: RemoteViews, dark: Boolean, colors: WidgetColors) {
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light,
        )
        views.setImageViewResource(
            R.id.widget_skull,
            if (dark) R.drawable.widget_skull_dark else R.drawable.widget_skull_light,
        )
        views.setTextColor(R.id.widget_chronometer, colors.main)
        views.setInt(
            R.id.widget_toggle,
            "setBackgroundResource",
            if (dark) R.drawable.widget_button_dark_primary else R.drawable.widget_button_light_primary,
        )
        views.setInt(
            R.id.widget_stop,
            "setBackgroundResource",
            if (dark) R.drawable.widget_button_dark_secondary else R.drawable.widget_button_light_secondary,
        )
    }

    private fun setPixelLabel(
        views: RemoteViews,
        context: Context,
        viewId: Int,
        text: String,
        textSizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ) {
        views.setImageViewBitmap(viewId, renderPixelText(context, text, textSizeSp, color, bold))
    }

    private fun renderPixelText(
        context: Context,
        text: String,
        textSizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ): Bitmap {
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        val density = context.resources.displayMetrics.density
        val typeface = loadPixelTypeface(context)
        val paint = Paint().apply {
            isAntiAlias = false
            isDither = false
            isSubpixelText = false
            this.color = color
            textSize = textSizeSp * scaledDensity
            this.typeface = typeface
            isFakeBoldText = bold
        }
        val fm = paint.fontMetrics
        val padding = (2f * density).coerceAtLeast(2f)
        val width = ceil(paint.measureText(text) + padding * 2f).toInt().coerceAtLeast(1)
        val height = ceil((fm.descent - fm.ascent) + padding * 2f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawText(text, padding, padding - fm.ascent, paint)
        }
    }

    private fun formatWidgetTime(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun widgetChronometerFormat(displayMs: Long): String {
        val hours = displayMs.coerceAtLeast(0L) / 3_600_000L
        return when {
            hours == 0L -> "00:%s"
            hours < 10L -> "0%s"
            else -> "%s"
        }
    }

    private fun loadPixelTypeface(context: Context): Typeface {
        val id = context.resources.getIdentifier(
            "fusion_pixel_12px_proportional",
            "font",
            context.packageName,
        )
        return if (id != 0) {
            runCatching { context.resources.getFont(id) }.getOrDefault(Typeface.DEFAULT)
        } else {
            Typeface.DEFAULT
        }
    }

    private class WidgetColors(dark: Boolean) {
        val main: Int = if (dark) Color.rgb(241, 238, 230) else Color.rgb(16, 16, 16)
        val secondary: Int = if (dark) Color.rgb(174, 174, 170) else Color.rgb(89, 89, 89)
        val primaryText: Int = if (dark) Color.rgb(16, 16, 16) else Color.WHITE
    }

    companion object {
        private const val ACTION_TOGGLE = "com.idleskull.app.widget.TOGGLE"
        private const val ACTION_STOP = "com.idleskull.app.widget.STOP"
        private const val ACTION_COUNTDOWN_FINISH = "com.idleskull.app.widget.COUNTDOWN_FINISH"
        private const val ACTION_FORMAT_BOUNDARY = "com.idleskull.app.widget.FORMAT_BOUNDARY"

        private fun updateCountdownCompletionAlarm(
            context: Context,
            active: com.idleskull.app.model.ActiveTimer?,
            displayMs: Long,
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                35,
                Intent(context, TimerWidgetProvider::class.java)
                    .setAction(ACTION_COUNTDOWN_FINISH)
                    .setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (
                active != null &&
                active.mode == TimerMode.COUNT_DOWN &&
                active.status == TimerStatus.RUNNING &&
                displayMs > 0L
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + displayMs,
                    pending,
                )
            } else {
                alarmManager.cancel(pending)
            }
        }

        private fun updateFormatBoundaryAlarm(
            context: Context,
            active: com.idleskull.app.model.ActiveTimer?,
            displayMs: Long,
            nowWall: Long,
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                36,
                Intent(context, TimerWidgetProvider::class.java)
                    .setAction(ACTION_FORMAT_BOUNDARY)
                    .setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (active == null || active.status != TimerStatus.RUNNING) {
                alarmManager.cancel(pending)
                return
            }
            val oneHour = 3_600_000L
            val tenHours = 36_000_000L
            val delayMs = if (active.mode == TimerMode.COUNT_DOWN) {
                when {
                    displayMs > tenHours -> displayMs - tenHours
                    displayMs > oneHour -> displayMs - oneHour
                    else -> null
                }
            } else {
                val elapsed = active.elapsedAt(nowWall)
                when {
                    elapsed < oneHour -> oneHour - elapsed
                    elapsed < tenHours -> tenHours - elapsed
                    else -> null
                }
            }
            if (delayMs != null && delayMs > 0L) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMs,
                    pending,
                )
            } else {
                alarmManager.cancel(pending)
            }
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TimerWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { id ->
                manager.updateAppWidget(id, TimerWidgetProvider().buildViews(context))
            }
        }

    }
}
