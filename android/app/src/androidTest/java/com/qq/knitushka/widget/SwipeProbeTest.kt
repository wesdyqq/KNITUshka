package com.qq.knitushka.widget

import android.appwidget.AppWidgetHostView
import android.widget.ListView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.qq.knitushka.R
import com.qq.knitushka.data.today
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SwipeProbeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun squareWidgetRespondsToRepeatedHorizontalGestures() = verifyPaging(240, 240, "square")

    @Test fun rectangularWidgetUsesItsWholeAreaAndStillScrollsHorizontally() = verifyPaging(160, 280, "rectangle")

    @Test fun launcherDimensionsAreNotReducedToASquare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val portrait = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT
        })
        val options = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 170)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 280)
        }
        assertEquals(160 to 280, SwipeProbeWidgetProvider.dimensions(portrait, options))
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            options.putParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(android.util.SizeF(280f, 160f), android.util.SizeF(160f, 280f)))
            assertEquals(160 to 280, SwipeProbeWidgetProvider.dimensions(portrait, options))
        }
    }

    private fun verifyPaging(widthDp: Int, heightDp: Int, label: String) {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 31)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val days = (0L..6L).map { WidgetSnapshot(today().plusDays(it), "861-72", "Пар нет", listOf(
            WidgetLesson(1, "Математический анализ. Часть 1", "08:00 – 09:30", "Л-223"),
            WidgetLesson(2, "Информатика", "09:40 – 11:10", "В-3026"))) }
        lateinit var list: ListView
        compose.setContent {
            AndroidView(modifier = Modifier.size(widthDp.dp, heightDp.dp).testTag("swipe_probe_host"), factory = {
                val host = AppWidgetHostView(it)
                val root = SwipeProbeWidgetProvider.views(context, -725, days, widthDp, heightDp).apply(context, host)
                host.addView(root)
                list = root.findViewById(R.id.swipe_probe_stack)
                host
            })
        }
        compose.waitForIdle()
        var before = -1
        fun offset(): Int = list.firstVisiblePosition * list.height - (list.getChildAt(0)?.top ?: 0)
        compose.waitUntil(5_000) { list.childCount > 0 }
        compose.runOnIdle {
            before = offset()
            val density = context.resources.displayMetrics.density
            val body = list.getChildAt(0).findViewById<android.view.View>(R.id.swipe_probe_body)
            assertEquals((widthDp * density).toInt(), body.width)
            assertEquals((heightDp * density).toInt(), body.height)
            val visible = android.graphics.Rect()
            assertTrue(body.getGlobalVisibleRect(visible))
            assertTrue(kotlin.math.abs(visible.width() - body.width) <= 2)
            assertTrue(kotlin.math.abs(visible.height() - body.height) <= 2)
        }
        val bitmap = compose.onNodeWithTag("swipe_probe_host").captureToImage().asAndroidBitmap()
        java.io.File(context.cacheDir, "swipe-probe-$label.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        repeat(2) {
            compose.onNodeWithTag("swipe_probe_host").performTouchInput { swipeLeft(durationMillis = 500) }
            compose.waitForIdle()
            compose.waitUntil(5_000) { kotlin.math.abs(offset() - before) > 30 }
            compose.runOnIdle { assertNotEquals(before, offset()); before = offset() }
        }
        compose.onNodeWithTag("swipe_probe_host").performTouchInput { swipeRight(durationMillis = 500) }
        compose.waitForIdle()
        compose.waitUntil(5_000) { kotlin.math.abs(offset() - before) > 30 }
    }
}
