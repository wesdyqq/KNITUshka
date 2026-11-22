package org.knitushka.vivoprobe

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.qq.knitushka.KnituApplication
import com.qq.knitushka.R
import com.qq.knitushka.data.today
import com.qq.knitushka.data.ScheduleRepository
import com.qq.knitushka.widget.ScheduleWidgetProvider
import com.qq.knitushka.widget.WidgetStorage
import com.qq.knitushka.widget.WidgetSnapshot
import com.qq.knitushka.widget.asWidgetLesson
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Vendor demo identity only. No permission changes or production integration. */
class ProbeProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SELECT) {
            val manager = AppWidgetManager.getInstance(context)
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id !in manager.getAppWidgetIds(ComponentName(context, ProbeProvider::class.java))) return
            val date = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrNull() ?: return
            context.getSharedPreferences("vivo_hybrid", Context.MODE_PRIVATE).edit()
                .putString("date:$id", date.toString()).putLong("changed:$id", System.currentTimeMillis()).apply()
            Log.i(TAG, "SELECT widget=$id date=$date")
            // No RemoteViews re-inflation on navigation: the native pager owns the live transition.
            if (!usesNativePager(context)) onUpdate(context, manager, intArrayOf(id))
        } else if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, ProbeProvider::class.java)))
        } else super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) { render(context, manager, appWidgetIds) }
            } catch (error: Exception) {
                if (error !is CancellationException) Log.e(TAG, "Hybrid update failed", error)
            } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = context.getSharedPreferences("vivo_hybrid", Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { editor.remove("date:$it").remove("changed:$it") }
        editor.apply()
    }

    @SuppressLint("RemoteViewLayout") // Deliberate Vivo-only custom loading probe, debug sources only.
    private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val repo = (context.applicationContext as KnituApplication).repository
        if (!usesNativePager(context)) {
            renderButtonFallback(context, manager, ids, repo)
            return
        }
        val group = repo.selection()
        val now = today()
        val first = now.minusDays(now.dayOfWeek.value.toLong() - 1).minusWeeks(1)
        val days = JSONArray()
        val weeks = (0L..2L).associateWith { group?.let { g -> repo.cached(g, first.plusWeeks(it)) } }
        repeat(21) { index ->
            val date = first.plusDays(index.toLong())
            val cached = weeks[index.toLong() / 7]
            val rows = JSONArray()
            cached?.week?.lessons.orEmpty().filter { it.date == date }.sortedBy { it.start }.forEach { lesson ->
                rows.put(JSONObject().put("number", lesson.number).put("subject", lesson.subject)
                    .put("time", "${lesson.start} – ${lesson.end}").put("room", lesson.room))
            }
            days.put(JSONObject().put("date", date.toString()).put("lessons", rows).put("empty", when {
                group == null -> "Выбери группу в Vivo probe"
                cached == null -> "Неделя не загружена в Vivo probe"
                else -> "На этот день пар нет"
            }))
        }
        val prefs = context.getSharedPreferences("vivo_hybrid", Context.MODE_PRIVATE)
        ids.forEach { id ->
            val changed = prefs.getLong("changed:$id", 0)
            val selected = if (System.currentTimeMillis() - changed < 600_000)
                prefs.getString("date:$id", now.toString()) else now.toString()
            val payload = JSONObject().put("widgetId", id).put("group", group?.name ?: "КНИТУшка")
                .put("selected", selected).put("days", days)
            val views = RemoteViews(context.packageName, R.layout.vivo_hybrid_widget)
            views.setTextViewText(R.id.vivo_payload, payload.toString())
            views.removeAllViews(R.id.vivo_tokens)
            views.removeAllViews(R.id.vivo_open_tokens)
            repeat(days.length()) { index ->
                val date = days.getJSONObject(index).getString("date")
                val intent = Intent(context, ProbeProvider::class.java).setAction(SELECT)
                    .setData(Uri.parse("knitu-vivo://widget/$id/$date"))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("date", date)
                val token = RemoteViews(context.packageName, R.layout.vivo_hybrid_token)
                token.setOnClickPendingIntent(R.id.vivo_day_token, PendingIntent.getBroadcast(context, id,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.addView(R.id.vivo_tokens, token)
                val open = ScheduleWidgetProvider.openDate(context, id, LocalDate.parse(date))
                val openToken = RemoteViews(context.packageName, R.layout.vivo_hybrid_token)
                openToken.setOnClickPendingIntent(R.id.vivo_day_token, PendingIntent.getActivity(context, id,
                    open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.addView(R.id.vivo_open_tokens, openToken)
            }
            manager.updateAppWidget(id, views)
            Log.i(TAG, "UPDATE widget=$id group=${group?.name} days=${days.length()}")
        }
    }

    private suspend fun renderButtonFallback(context: Context, manager: AppWidgetManager, ids: IntArray,
        repo: ScheduleRepository) {
        val prefs = context.getSharedPreferences("vivo_hybrid", Context.MODE_PRIVATE)
        val group = repo.selection()
        ids.forEach { id ->
            val date = if (System.currentTimeMillis() - prefs.getLong("changed:$id", 0) < 600_000)
                runCatching { LocalDate.parse(prefs.getString("date:$id", null)) }.getOrNull() ?: today() else today()
            val cached = group?.let { repo.cached(it, date) }
            val snapshot = WidgetSnapshot(date, group?.name ?: "КНИТУшка", when {
                group == null -> "Выбери группу в приложении"
                cached == null -> "Неделя не загружена"
                else -> "На этот день пар нет"
            }, cached?.week?.lessons.orEmpty().filter { it.date == date }.sortedBy { it.start }.map { it.asWidgetLesson() })
            WidgetStorage(context).saveSnapshot(id, snapshot)
            val views = ScheduleWidgetProvider.views(context, id, snapshot)
            listOf(R.id.widget_previous to -1L, R.id.widget_next to 1L).forEach { (button, offset) ->
                val target = date.plusDays(offset).toString()
                val intent = Intent(context, ProbeProvider::class.java).setAction(SELECT)
                    .setData(Uri.parse("knitu-vivo://widget/$id/$target"))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("date", target)
                views.setOnClickPendingIntent(button, PendingIntent.getBroadcast(context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(id, views)
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_lessons)
            Log.i(TAG, "FALLBACK widget=$id date=$date (standard RemoteViews, buttons only)")
        }
    }

    companion object {
        internal fun usesNativePager(context: Context): Boolean {
            val launcher = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            return launcher == "com.bbk.launcher2" && context.packageName == "com.example.tjktest.widgetdemoapp"
        }
        private const val SELECT = "org.knitushka.vivoprobe.SELECT"
        private const val TAG = "KnituVivoProbe"
    }
}
