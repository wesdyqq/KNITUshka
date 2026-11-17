package com.qq.knitushka.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.qq.knitushka.ScheduleUiState
import com.qq.knitushka.data.Institute
import com.qq.knitushka.data.StudyGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = ScheduleUiState(starting = false, theme = "system",
        group = StudyGroup("47802", "861-72", Institute("320", "ИУАИТ", "Институт")))

    @Test fun themeSelectionAndAllActionsRemainAvailable() {
        var theme by mutableStateOf("system")
        var action = ""
        compose.setContent { KnituTheme(theme) {
            SettingsScreen(state.copy(theme = theme), "1.0.3", { theme = it },
                { action = "group" }, { action = "widget" }, { action = "close" }, { action = it })
        } }
        compose.onNodeWithText("Как на устройстве").assertIsSelected()
        saveScreenshot("settings-light.png")
        compose.onNodeWithText("Тёмное").performClick().assertIsSelected()
        saveScreenshot("settings-dark.png")
        compose.onNodeWithText("Светлое").performClick().assertIsSelected()
        compose.onNodeWithText("Сменить группу").performScrollTo().performClick()
        assertEquals("group", action)
        compose.onNodeWithText("Добавить виджет").performScrollTo().performClick()
        assertEquals("widget", action)
        compose.onNodeWithText("Открыть проект на GitHub").performScrollTo().performClick()
        assertEquals("https://github.com/wesdyqq/KNITUshka", action)
        compose.onNodeWithText("Открыть лицензию MIT").performScrollTo().performClick()
        assertEquals("https://github.com/wesdyqq/KNITUshka/blob/main/LICENSE", action)
        compose.onNodeWithContentDescription("Закрыть настройки").performClick()
        assertEquals("close", action)
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(context.cacheDir, name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun narrowScreenWithDoubleFontScaleKeepsControlsReachable() {
        compose.setContent { KnituTheme("dark") {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                Box(Modifier.width(320.dp)) { SettingsScreen(state, "1.0.3", {}, {}, {}, {}, {}) }
            }
        } }
        compose.onNodeWithText("Как на устройстве").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Тёмное").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Сменить группу").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Добавить виджет").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Открыть лицензию MIT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("© 2026 Камиль Сафьянов").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть настройки").assertIsDisplayed()
    }
}
