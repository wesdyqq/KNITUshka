package com.qq.knitushka.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.widget.TextView
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.ListView
import android.widget.BaseAdapter
import android.view.ViewGroup
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.TextUtils
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qq.knitushka.MainActivity
import com.qq.knitushka.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WidgetIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val id = -721

    @Test fun stateAndSnapshotSurviveNewStorageInstance() {
        val storage = WidgetStorage(context)
        try {
            val state = WidgetDayState(LocalDate.of(2026, 11, 18), 123456L)
            val snapshot = WidgetSnapshot(state.date!!, "861-72", "Нет занятий", listOf(
                WidgetLesson(3, "Математический анализ", "11:20 – 12:50", "Б-322")))
            storage.saveState(id, state)
            storage.saveSnapshot(id, snapshot)
            assertEquals(state, WidgetStorage(context).state(id))
            assertEquals(snapshot, WidgetStorage(context).snapshot(id))
        } finally { storage.remove(id) }
    }

    @Test fun providerIsRegisteredAndUsesTwoByThreeCells() {
        val provider = AppWidgetManager.getInstance(context).installedProviders.single {
            it.provider == ComponentName(context, ScheduleWidgetProvider::class.java)
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            assertEquals(2, provider.targetCellWidth)
            assertEquals(3, provider.targetCellHeight)
        }
        assertTrue(provider.minWidth > 0 && provider.minHeight > 0)
    }

    @Test fun remoteViewsInflateAndExposeTheSelectedDateAndSevenDots() {
        val date = LocalDate.of(2026, 11, 18)
        val snapshot = WidgetSnapshot(date, "861-72", "На этот день пар нет", emptyList())
        instrumentation.runOnMainSync {
            val views = ScheduleWidgetProvider.views(context, id, snapshot)
            val view = views.apply(context, AppWidgetHostView(context))
            assertEquals("861-72", view.findViewById<TextView>(R.id.widget_group).text.toString())
            assertEquals("18/11", view.findViewById<TextView>(R.id.widget_date).text.toString())
            assertEquals("День недели 3 из 7", view.findViewById<ImageView>(R.id.widget_dots).contentDescription)
            assertNotNull(view.findViewById<ImageButton>(R.id.widget_previous).drawable)
            assertNotNull(view.findViewById<ImageButton>(R.id.widget_next).drawable)
        }
    }

    @Test fun dotsHaveSeparateInactiveAndActiveColorsForEveryDay() {
        val density = context.resources.displayMetrics.density
        val active = context.getColor(R.color.widget_accent)
        val inactive = context.getColor(R.color.widget_dot_inactive)
        for (selected in 1..7) {
            val bitmap = ScheduleWidgetProvider.dayDotsImage(context, selected)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.contains(active))
            assertTrue(pixels.contains(inactive))
            for (day in 1..7) {
                val center = bitmap.getPixel(((2.5f + (day - 1) * 5) * density).toInt(), (4 * density).toInt())
                assertEquals(if (day == selected) active else android.graphics.Color.TRANSPARENT, center)
            }
        }
    }

    @Test fun longWidgetSubjectIsBoldAndEllipsizedAtTwoLines() {
        instrumentation.runOnMainSync {
            val row = WidgetLesson(10, "Очень длинное название учебной дисциплины, которое не должно занять больше двух строк в узком виджете", "11:20 – 12:50", "Б-322")
            val view = ScheduleWidgetService.lessonViews(context.packageName, row, Intent()).apply(context, AppWidgetHostView(context))
            val width = (150 * context.resources.displayMetrics.density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, width, view.measuredHeight)
            val title = view.findViewById<TextView>(R.id.widget_lesson_subject)
            assertEquals(2, title.maxLines)
            assertEquals(TextUtils.TruncateAt.END, title.ellipsize)
            assertTrue(title.typeface.isBold)
            assertTrue(view.findViewById<TextView>(R.id.widget_lesson_number).typeface.isBold)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                assertEquals(900, view.findViewById<TextView>(R.id.widget_lesson_number).typeface.weight)
            }
            assertEquals(row.subject, title.text.toString()) // Full value remains available to accessibility.
            assertTrue(title.layout.lineCount <= 2)
            assertTrue(title.layout.getEllipsisCount(title.layout.lineCount - 1) > 0)
        }
    }

    @Test fun headerAndNavigationFitNarrowWidgetsInBothAppearances() {
        instrumentation.runOnMainSync {
            for (night in listOf(false, true)) {
                val config = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val themed = context.createConfigurationContext(config)
                val density = themed.resources.displayMetrics.density
                val rows = listOf(WidgetLesson(1, "Математический анализ. Часть 1", "08:00 – 09:30", "Л-223"),
                    WidgetLesson(2, "Элективные курсы по физической культуре и спорту", "09:40 – 11:10", "М-6(12)"))
                for (widthDp in listOf(110, 140, 190)) {
                    val snapshot = WidgetSnapshot(LocalDate.of(2026, 11, 18), "861-72", "Пар нет", rows)
                    val root = ScheduleWidgetProvider.views(themed, id, snapshot).apply(themed, AppWidgetHostView(themed))
                    root.findViewById<ListView>(R.id.widget_lessons).adapter = object : BaseAdapter() {
                        override fun getCount() = rows.size
                        override fun getItem(position: Int) = rows[position]
                        override fun getItemId(position: Int) = position.toLong()
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                            ScheduleWidgetService.lessonViews(themed.packageName, rows[position], Intent()).apply(themed, parent)
                    }
                    val width = (widthDp * density).toInt()
                    val height = (280 * density).toInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                    val previous = root.findViewById<View>(R.id.widget_previous)
                    val next = root.findViewById<View>(R.id.widget_next)
                    val dots = root.findViewById<View>(R.id.widget_dots)
                    assertTrue(previous.width >= (44 * density).toInt())
                    assertTrue(next.width >= (44 * density).toInt())
                    val backgroundPadding = android.graphics.Rect()
                    previous.background.getPadding(backgroundPadding)
                    assertEquals((4 * density).toInt(), backgroundPadding.left)
                    assertEquals((4 * density).toInt(), backgroundPadding.right)
                    assertTrue(previous.right <= dots.left && dots.right <= next.left)
                    assertTrue(dots.width > 0)
                    val date = root.findViewById<TextView>(R.id.widget_date)
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        for (viewId in listOf(R.id.widget_date, R.id.widget_weekday, R.id.widget_group)) {
                            assertEquals(900, root.findViewById<TextView>(viewId).typeface.weight)
                        }
                    }
                    assertEquals("18/11", date.text.toString())
                    assertTrue("Date clipped at ${widthDp}dp", date.layout.getLineWidth(0) <= date.width + 1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    java.io.File(context.cacheDir, "widget-${widthDp}-${if (night) "dark" else "light"}.png").outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                }
            }
        }
    }

    @Test fun openingWidgetUsesAnExplicitActivityAndSelectedIsoDate() {
        val date = LocalDate.of(2026, 11, 18)
        val intent = ScheduleWidgetProvider.openDate(context, id, date)
        assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
        assertEquals(date.toString(), intent.getStringExtra(ScheduleWidgetProvider.EXTRA_DATE))
        assertEquals(date.toString(), ScheduleWidgetProvider.dateFromIntent(context, intent))
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test fun boundWidgetNavigatesAndResetsAnExpiredDate() {
        // Dedicated emulator: grantbind for com.qq.knitushka and com.qq.knitushka.test.
        // No host-wide data or application settings are removed by this test.
        val host = AppWidgetHost(context, 721)
        val manager = AppWidgetManager.getInstance(context)
        val widgetId = host.allocateAppWidgetId()
        val storage = WidgetStorage(context)
        try {
            assumeTrue("Grant widget binding on the test emulator to run the host integration check",
                manager.bindAppWidgetIdIfAllowed(widgetId, ComponentName(context, ScheduleWidgetProvider::class.java)))
            ScheduleWidgetProvider.requestUpdate(context)
            await { storage.snapshot(widgetId) != null }
            val now = System.currentTimeMillis()
            val today = WidgetDayState().selectedDate(now)
            context.sendBroadcast(Intent(context, ScheduleWidgetProvider::class.java)
                .setAction("com.qq.knitushka.widget.NEXT")
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            await { storage.snapshot(widgetId)?.date == today.plusDays(1) }
            val open = ScheduleWidgetProvider.openDate(context, widgetId, today.plusDays(1))
            assertEquals(today.plusDays(1).toString(), ScheduleWidgetProvider.dateFromIntent(context, open))
            storage.saveState(widgetId, WidgetDayState(today.plusDays(1), now - 600_001))
            assertEquals(today.toString(), ScheduleWidgetProvider.dateFromIntent(context, open))
            ScheduleWidgetProvider.requestUpdate(context)
            await { storage.snapshot(widgetId)?.date == today && storage.state(widgetId).date == null }
        } finally {
            host.deleteAppWidgetId(widgetId)
            storage.remove(widgetId)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (!condition()) {
            assertTrue("Widget update timed out", android.os.SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(25)
        }
    }
}
