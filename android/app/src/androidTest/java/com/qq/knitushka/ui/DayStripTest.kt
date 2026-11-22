package com.qq.knitushka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry

class DayStripTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 17)

    @Test fun pastDayShowsRightArrowAndClickReturnsToToday() {
        var date by mutableStateOf(today.minusDays(2))
        compose.setContent { KnituTheme("light") { DayStrip(date, today, emptySet(), { date = it }, { date = today }) } }
        compose.onNodeWithTag("today_arrow_right").assertIsDisplayed().performClick()
        compose.onNodeWithTag("today_arrow_right").assertDoesNotExist()
        compose.onNodeWithTag("today_arrow_left").assertDoesNotExist()
        compose.onNodeWithText("Сегодня").assertDoesNotExist()
        compose.onNodeWithText("На сегодня").assertDoesNotExist()
    }

    @Test fun futureDayShowsLeftArrowWithAccessibleName() {
        compose.setContent { KnituTheme("light") { DayStrip(today.plusDays(3), today, emptySet(), {}, {}) } }
        compose.onNodeWithTag("today_arrow_left").assertIsDisplayed()
        compose.onNodeWithTag("today_arrow_right").assertDoesNotExist()
        compose.onNodeWithContentDescription("Вернуться к сегодняшнему дню").assertIsDisplayed()
    }

    @Test fun todayHasNoReturnButton() {
        compose.setContent { KnituTheme("light") { DayStrip(today, today, emptySet(), {}, {}) } }
        compose.onNodeWithContentDescription("Вернуться к сегодняшнему дню").assertDoesNotExist()
    }

    @Test fun arrowVisibilityDoesNotMoveTheWeek() {
        val selected = today
        var systemToday by mutableStateOf(today)
        compose.setContent { KnituTheme("light") { DayStrip(selected, systemToday, emptySet(), {}, {}) } }
        val tag = "day_$selected"
        val before = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        saveStrip("today")
        for (offset in listOf(-1L, 0L, 1L, 0L)) {
            compose.runOnIdle { systemToday = today.plusDays(offset) }
            compose.waitForIdle()
            assertEquals(before, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
            if (offset != 0L) saveStrip(if (offset < 0) "left" else "right")
        }
    }

    private fun saveStrip(name: String) {
        val image = compose.onNodeWithTag("day_strip").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir, "day-strip-$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun leftArrowFadesInPlaceInsteadOfJumpingToTheRight() {
        var systemToday by mutableStateOf(today.minusDays(1))
        compose.setContent { KnituTheme("light") { DayStrip(today, systemToday, emptySet(), {}, {}) } }
        compose.onNodeWithTag("today_arrow_left").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { systemToday = today }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("today_arrow_left").assertExists()
        compose.onNodeWithTag("today_arrow_right").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithTag("today_arrow_left").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }
}
