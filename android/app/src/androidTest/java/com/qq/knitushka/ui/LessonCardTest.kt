package com.qq.knitushka.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.qq.knitushka.data.Lesson
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LessonCardTest {
    @get:Rule val compose = createComposeRule()
    private val lesson = Lesson(LocalDate.of(2026, 11, 17), 3, "11:20", "12:50", "Б-322",
        "Основы российской государственности", "ЛЕКЦ", listOf("Тузиков А.Р."), "Подгруппа 1")

    @Test fun numberAndTimeAreLeftOfTheLargerSubjectAndMetadata() {
        compose.setContent { KnituTheme("light") { LessonCard(lesson, active = true) } }
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("11:20 –").assertIsDisplayed()
        compose.onNodeWithText("12:50").assertIsDisplayed()
        compose.onNodeWithText(lesson.subject).assertIsDisplayed()
        compose.onNodeWithText("Идёт сейчас").assertIsDisplayed()
        compose.onNodeWithText("11:20 — 12:50").assertDoesNotExist()
        compose.onNodeWithTag("lesson_number_circle").assertIsDisplayed()
        val circle = compose.onNodeWithTag("lesson_number_circle").getUnclippedBoundsInRoot()
        assertTrue(kotlin.math.abs(((circle.right - circle.left) - (circle.bottom - circle.top)).value) < 1f)
        val start = compose.onNodeWithText("11:20 –").getUnclippedBoundsInRoot()
        assertTrue(start.top > circle.bottom)
        val end = compose.onNodeWithText("12:50").getUnclippedBoundsInRoot()
        assertTrue(kotlin.math.abs((start.left - end.left).value) < 1f)
        assertTrue(end.top >= start.bottom)
        val rail = compose.onNodeWithTag("lesson_time_rail").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(lesson.subject).getUnclippedBoundsInRoot()
        val room = compose.onNodeWithText(lesson.room).getUnclippedBoundsInRoot()
        assertTrue(rail.right <= title.left)
        assertTrue(rail.right <= room.left)
    }

    @Test fun longSubjectAndMetadataRemainReachableAtDoubleFontScale() {
        compose.setContent {
            KnituTheme("dark") {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    Column(Modifier.widthIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        LessonCard(lesson, active = false)
                    }
                }
            }
        }
        compose.onNodeWithText(lesson.subject).assertTextEquals(lesson.subject)
        compose.onNodeWithText("Подгруппа 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Тузиков А.Р.").performScrollTo().assertIsDisplayed()
        val rail = compose.onNodeWithTag("lesson_time_rail").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(lesson.subject).getUnclippedBoundsInRoot()
        assertTrue(rail.right <= title.left)
    }
}
