package com.qq.knitushka.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.res.Configuration
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.qq.knitushka.KnituApplication
import com.qq.knitushka.MainActivity
import com.qq.knitushka.R
import com.qq.knitushka.data.today
import kotlinx.coroutines.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Debug-only feasibility probe, NOT a replacement for the persistent daily widget.
 * ListView does not notify the provider about its scroll position and cannot snap to a page.
 * Consequently this probe deliberately makes no promise about persistence or ten-minute reset.
 */
class SwipeProbeWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, SwipeProbeWidgetProvider::class.java)))
        } else super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    val repo = (context.applicationContext as KnituApplication).repository
                    val group = repo.selection()
                    val days = (0L..6L).map { offset ->
                        val date = today().plusDays(offset)
                        val cached = group?.let { repo.cached(it, date) }
                        WidgetSnapshot(date, group?.name ?: "Выбери группу", if (cached == null) "Открой неделю в приложении" else "Пар нет",
                            cached?.week?.lessons.orEmpty().filter { it.date == date }.sortedBy { it.start }.map { it.asWidgetLesson() })
                    }
                    appWidgetIds.forEach { id ->
                        val options = manager.getAppWidgetOptions(id)
                        val size = dimensions(context, options)
                        manager.updateAppWidget(id, views(context, id, days, size.first, size.second))
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                android.util.Log.w("SwipeProbe", "Cannot prepare experimental widget", error)
            } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    companion object {
        @Suppress("DEPRECATION")
        internal fun dimensions(context: Context, options: Bundle): Pair<Int, Int> {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (Build.VERSION.SDK_INT >= 31) {
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
                val size = if (landscape) sizes.maxByOrNull { it.width } else sizes.minByOrNull { it.width }
                if (size != null) return size.width.roundToInt().coerceAtLeast(1) to size.height.roundToInt().coerceAtLeast(1)
            }
            val widthKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
            val heightKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            return options.getInt(widthKey, 180).coerceAtLeast(110) to options.getInt(heightKey, 270).coerceAtLeast(110)
        }

        internal fun views(context: Context, id: Int, days: List<WidgetSnapshot>, widthDp: Int = 240, heightDp: Int = 240): RemoteViews =
            RemoteViews(context.packageName, R.layout.swipe_probe_widget).apply {
                if (Build.VERSION.SDK_INT < 31) {
                    setViewVisibility(R.id.swipe_probe_stack, View.GONE)
                    setViewVisibility(R.id.swipe_probe_unsupported, View.VISIBLE)
                    return@apply
                }
                val collection = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
                days.forEach { snapshot -> collection.addItem(snapshot.date.toEpochDay(), page(context, snapshot, widthDp, heightDp)) }
                setViewLayoutWidth(R.id.swipe_probe_stack, heightDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.swipe_probe_stack, widthDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                setRemoteAdapter(R.id.swipe_probe_stack, collection.build())
                val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse("knitushka://swipe-probe/$id"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setPendingIntentTemplate(R.id.swipe_probe_stack, PendingIntent.getActivity(context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
            }

        private fun page(context: Context, snapshot: WidgetSnapshot, widthDp: Int, heightDp: Int): RemoteViews =
            RemoteViews(context.packageName, R.layout.swipe_probe_page).apply {
                if (Build.VERSION.SDK_INT >= 31) {
                    setViewLayoutHeight(R.id.swipe_probe_page_viewport, widthDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutWidth(R.id.swipe_probe_body, widthDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                    setViewLayoutHeight(R.id.swipe_probe_body, heightDp.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
                }
                val ru = Locale.forLanguageTag("ru-RU")
                setTextViewText(R.id.widget_date, snapshot.date.format(DateTimeFormatter.ofPattern("dd/MM", ru)))
                setTextViewText(R.id.widget_weekday, snapshot.date.format(DateTimeFormatter.ofPattern("EEEE", ru))
                    .replaceFirstChar { it.uppercase(ru) })
                setTextViewText(R.id.widget_group, snapshot.group)
                snapshot.lessons.take(2).forEach { lesson ->
                    addView(R.id.swipe_probe_lessons, RemoteViews(context.packageName, R.layout.schedule_widget_lesson).apply {
                        setTextViewText(R.id.widget_lesson_number, lesson.number.toString())
                        setTextViewText(R.id.widget_lesson_subject, lesson.subject)
                        setTextViewText(R.id.widget_lesson_time, lesson.time)
                        setTextViewText(R.id.widget_lesson_room, lesson.room)
                    })
                }
                val extra = (snapshot.lessons.size - 2).coerceAtLeast(0)
                setTextViewText(R.id.swipe_probe_hint, when {
                    snapshot.lessons.isEmpty() -> snapshot.emptyMessage
                    extra > 0 -> "Ещё пар: $extra · нажми, чтобы открыть"
                    else -> "Эксперимент со свайпами"
                })
                setImageViewBitmap(R.id.swipe_probe_dots, ScheduleWidgetProvider.dayDotsImage(context, snapshot.date.dayOfWeek.value))
                setOnClickFillInIntent(R.id.swipe_probe_page, Intent().putExtra(ScheduleWidgetProvider.EXTRA_DATE, snapshot.date.toString()))
            }
    }
}
