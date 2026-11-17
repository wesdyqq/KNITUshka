package com.qq.knitushka.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.qq.knitushka.KnituApplication
import com.qq.knitushka.MainActivity
import com.qq.knitushka.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) { requestUpdate(context) }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PINNED) {
            Toast.makeText(context, "Виджет добавлен на главный экран", Toast.LENGTH_LONG).show()
            requestUpdate(context)
            return
        }
        if (intent.action !in setOf(REFRESH, PREVIOUS, NEXT, Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED)) {
            super.onReceive(context, intent)
            return
        }
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) {
                    gate.withLock {
                        val manager = AppWidgetManager.getInstance(context)
                        val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
                        val storage = WidgetStorage(context)
                        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                        val now = System.currentTimeMillis()
                        if (id in ids && intent.action in setOf(PREVIOUS, NEXT)) {
                            storage.saveState(id, storage.state(id).move(if (intent.action == NEXT) 1 else -1, now))
                        }
                        renderAll(context, manager, ids, now)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w("ScheduleWidget", "Widget update failed", error)
            } finally { pending.finish() }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetStorage(context).remove(it) }
        requestUpdate(context)
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshAlarm(context))
    }

    companion object {
        const val EXTRA_DATE = "com.qq.knitushka.SELECTED_DATE"
        private const val REFRESH = "com.qq.knitushka.widget.REFRESH"
        private const val PREVIOUS = "com.qq.knitushka.widget.PREVIOUS"
        private const val NEXT = "com.qq.knitushka.widget.NEXT"
        private const val PINNED = "com.qq.knitushka.widget.PINNED"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val gate = Mutex()

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java)).isNotEmpty()) {
                context.sendBroadcast(Intent(context, ScheduleWidgetProvider::class.java).setAction(REFRESH))
            }
        }

        /** True means only that the launcher accepted the request, not that it showed UI or pinned. */
        fun requestPin(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 26) return false
            val launcher = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            if (needsManualWidgetPin(Build.MANUFACTURER, launcher)) return false
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val preview = RemoteViews(context.packageName, R.layout.schedule_widget).apply {
                setTextViewText(R.id.widget_empty, "Расписание выбранной группы")
            }
            val extras = Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview) }
            val confirmed = PendingIntent.getBroadcast(context, 0,
                Intent(context, ScheduleWidgetProvider::class.java).setAction(PINNED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return manager.requestPinAppWidget(ComponentName(context, ScheduleWidgetProvider::class.java),
                extras, confirmed)
        }

        internal fun openDate(context: Context, id: Int, date: LocalDate) = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("knitushka://widget/$id/$date"))
            .putExtra(EXTRA_DATE, date.toString()).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun dateFromIntent(context: Context, intent: Intent): String? {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
            return if (id in ids) WidgetStorage(context).state(id).selectedDate(System.currentTimeMillis()).toString()
                else intent.getStringExtra(EXTRA_DATE)
        }

        private fun refreshAlarm(context: Context) = PendingIntent.getBroadcast(context, 0,
            Intent(context, ScheduleWidgetProvider::class.java).setAction(REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        @Suppress("DEPRECATION") // RemoteViews collections also support API 24–30.
        private suspend fun renderAll(context: Context, manager: AppWidgetManager, ids: IntArray, now: Long) {
            if (ids.isEmpty()) return
            val storage = WidgetStorage(context)
            val repo = (context.applicationContext as KnituApplication).repository
            val group = repo.selection()
            var updateAt = Long.MAX_VALUE
            for (id in ids) {
                var state = storage.state(id)
                if (state.isExpired(now)) { state = WidgetDayState(); storage.saveState(id, state) }
                val date = state.selectedDate(now)
                val cached = group?.let { repo.cached(it, date) }
                val rows = cached?.week?.lessons.orEmpty().filter { it.date == date }.sortedBy { it.start }.map { it.asWidgetLesson() }
                val message = when {
                    group == null -> "Выбери институт и группу в приложении"
                    cached == null -> "Неделя не сохранена. Открой приложение для загрузки"
                    else -> "На этот день пар нет"
                }
                val snapshot = WidgetSnapshot(date, group?.name ?: "КНИТУшка", message, rows)
                storage.saveSnapshot(id, snapshot)
                manager.updateAppWidget(id, views(context, id, snapshot))
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_lessons)
                updateAt = minOf(updateAt, state.nextUpdateAt(now))
            }
            // No exact-alarm permission: Android may defer delivery while asleep. Every render and
            // navigation also resolves expiry, so an expired custom date never survives an update.
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC,
                updateAt, refreshAlarm(context))
        }

        @Suppress("DEPRECATION")
        internal fun views(context: Context, id: Int, snapshot: WidgetSnapshot): RemoteViews {
            val date = snapshot.date
            val ru = Locale.forLanguageTag("ru-RU")
            return RemoteViews(context.packageName, R.layout.schedule_widget).apply {
                setTextViewText(R.id.widget_date, date.format(DateTimeFormatter.ofPattern("d MMMM", ru)))
                setTextViewText(R.id.widget_weekday, date.format(DateTimeFormatter.ofPattern("EEEE", ru))
                    .replaceFirstChar { it.uppercase(ru) })
                setTextViewText(R.id.widget_group, snapshot.group)
                setTextViewText(R.id.widget_empty, snapshot.emptyMessage)
                setTextViewText(R.id.widget_dots, (1..7).joinToString("  ") { if (it == date.dayOfWeek.value) "●" else "○" })
                setContentDescription(R.id.widget_dots, "День недели ${date.dayOfWeek.value} из 7")
                setEmptyView(R.id.widget_lessons, R.id.widget_empty)
                val adapter = Intent(context, ScheduleWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("knitushka://widget/$id/lessons"))
                setRemoteAdapter(R.id.widget_lessons, adapter)
                val open = openDate(context, id, date)
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                // Collection templates require a mutable PendingIntent, constrained to our Activity.
                setPendingIntentTemplate(R.id.widget_lessons, PendingIntent.getActivity(context, id xor Int.MIN_VALUE, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
                listOf(R.id.widget_previous to PREVIOUS, R.id.widget_next to NEXT).forEach { (view, action) ->
                    val click = Intent(context, ScheduleWidgetProvider::class.java).setAction(action)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setData(Uri.parse("knitushka://widget/$id/$action"))
                    setOnClickPendingIntent(view, PendingIntent.getBroadcast(context, id, click,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
        }
    }
}
