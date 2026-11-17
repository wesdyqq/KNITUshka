package com.qq.knitushka.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.qq.knitushka.ScheduleUiState
import com.qq.knitushka.data.Institute
import com.qq.knitushka.data.StudyGroup
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TimetableHeaderTest {
    @get:Rule val compose = createComposeRule()
    private val state = ScheduleUiState(starting = false,
        group = StudyGroup("47802", "861-72", Institute("320", "ИУАИТ", "Институт")),
        date = LocalDate.of(2026, 11, 17))

    @Test fun headerIsCompactAndTitleIsAboveTheGroup() {
        compose.setContent { KnituTheme("light") { TimetableHeader(state, {}, {}, {}) } }
        compose.onNodeWithText("Расписание").assertIsDisplayed()
        compose.onNodeWithText("2026").assertDoesNotExist()
        compose.onNodeWithText("В ритме твоих пар").assertDoesNotExist()
        val header = compose.onNodeWithTag("schedule_header").getUnclippedBoundsInRoot()
        val height = header.bottom - header.top
        assertTrue("Header is too tall: $height", height < 350.dp)
        val title = compose.onNodeWithText("Расписание").getUnclippedBoundsInRoot()
        val group = compose.onNodeWithText("861-72 · ИУАИТ").getUnclippedBoundsInRoot()
        assertTrue(title.top < group.top)
    }
}
