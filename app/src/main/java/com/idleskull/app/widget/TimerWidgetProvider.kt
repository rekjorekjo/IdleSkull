package com.idleskull.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.idleskull.app.MainActivity
import com.idleskull.app.R
import com.idleskull.app.data.TimerRepository
import com.idleskull.app.model.EndReason
import com.idleskull.app.model.TimerMode
import com.idleskull.app.model.TimerStatus

class TimerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
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
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val repo = TimerRepository(context)
        repo.ensureCountdownComplete()
        val active = repo.loadActive()
        val dark = repo.isDarkMode()
        val views = RemoteViews(context.packageName, R.layout.widget_timer)
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        applyTheme(views, dark)

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
            Intent(context, TimerWidgetProvider::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context,
            33,
            Intent(context, TimerWidgetProvider::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_toggle, toggle)
        views.setOnClickPendingIntent(R.id.widget_stop, stop)

        if (active == null) {
            views.setTextViewText(R.id.widget_status, "等待摆烂")
            views.setTextViewText(R.id.widget_toggle, "开始")
            views.setViewVisibility(R.id.widget_stop, View.GONE)
            views.setViewVisibility(R.id.widget_gap, View.GONE)
            views.setChronometer(R.id.widget_chronometer, nowElapsed, "%s", false)
            views.setChronometerCountDown(R.id.widget_chronometer, false)
        } else {
            val display = active.displayMsAt(nowWall)
            views.setTextViewText(
                R.id.widget_status,
                when {
                    active.status == TimerStatus.PAUSED -> "暂停中"
                    active.mode == TimerMode.COUNT_DOWN -> "倒计时摆烂中"
                    else -> "摆烂中"
                },
            )
            views.setTextViewText(
                R.id.widget_toggle,
                if (active.status == TimerStatus.RUNNING) "暂停" else "继续",
            )
            views.setViewVisibility(R.id.widget_stop, View.VISIBLE)
            views.setViewVisibility(R.id.widget_gap, View.VISIBLE)

            if (active.mode == TimerMode.COUNT_DOWN) {
                views.setChronometerCountDown(R.id.widget_chronometer, true)
                views.setChronometer(
                    R.id.widget_chronometer,
                    nowElapsed + display,
                    "%s",
                    active.status == TimerStatus.RUNNING,
                )
            } else {
                views.setChronometerCountDown(R.id.widget_chronometer, false)
                views.setChronometer(
                    R.id.widget_chronometer,
                    nowElapsed - display,
                    "%s",
                    active.status == TimerStatus.RUNNING,
                )
            }
        }
        return views
    }

    private fun applyTheme(views: RemoteViews, dark: Boolean) {
        val main = if (dark) Color.rgb(241, 238, 230) else Color.rgb(16, 16, 16)
        val secondary = if (dark) Color.rgb(174, 174, 170) else Color.rgb(89, 89, 89)
        val primaryText = if (dark) Color.rgb(16, 16, 16) else Color.WHITE

        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light,
        )
        views.setImageViewResource(
            R.id.widget_skull,
            if (dark) R.drawable.skull_backdrop_v2_dark else R.drawable.skull_backdrop_v2_light,
        )
        views.setTextColor(R.id.widget_title, main)
        views.setTextColor(R.id.widget_status, secondary)
        views.setTextColor(R.id.widget_chronometer, main)
        views.setInt(
            R.id.widget_toggle,
            "setBackgroundResource",
            if (dark) R.drawable.widget_button_dark_primary else R.drawable.widget_button_light_primary,
        )
        views.setTextColor(R.id.widget_toggle, primaryText)
        views.setInt(
            R.id.widget_stop,
            "setBackgroundResource",
            if (dark) R.drawable.widget_button_dark_secondary else R.drawable.widget_button_light_secondary,
        )
        views.setTextColor(R.id.widget_stop, main)
    }

    companion object {
        private const val ACTION_TOGGLE = "com.idleskull.app.widget.TOGGLE"
        private const val ACTION_STOP = "com.idleskull.app.widget.STOP"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TimerWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { id ->
                manager.updateAppWidget(id, TimerWidgetProvider().buildViews(context))
            }
        }
    }
}
