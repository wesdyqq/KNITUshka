package com.qq.knitushka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

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
}
