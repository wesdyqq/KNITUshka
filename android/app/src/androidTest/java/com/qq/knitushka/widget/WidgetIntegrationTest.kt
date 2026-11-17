package com.qq.knitushka.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.widget.TextView
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
            assertTrue(view.findViewById<TextView>(R.id.widget_date).text.toString().startsWith("18"))
            val dots = view.findViewById<TextView>(R.id.widget_dots).text.toString()
            assertEquals(1, dots.count { it == '●' })
            assertEquals(6, dots.count { it == '○' })
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
