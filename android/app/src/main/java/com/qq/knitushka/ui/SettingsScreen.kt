package com.qq.knitushka.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qq.knitushka.R
import com.qq.knitushka.ScheduleUiState

@Composable
internal fun SettingsDialog(state: ScheduleUiState, onTheme: (String) -> Unit, onChooseGroup: () -> Unit,
    onDismiss: () -> Unit, onPinWidget: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false)) {
        SettingsScreen(state, version, onTheme, onChooseGroup, onPinWidget, onDismiss, uri::openUri)
    }
}

@Composable
internal fun SettingsScreen(state: ScheduleUiState, version: String, onTheme: (String) -> Unit,
    onChooseGroup: () -> Unit, onPinWidget: () -> Unit, onDismiss: () -> Unit, onOpenLink: (String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Закрыть настройки") }
                Text("Настройки", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp).semantics { heading() })
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .testTag("settings_scroll").padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Image(painterResource(R.drawable.knitushka_mark), null, Modifier.size(56.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("КНИТУшка", style = MaterialTheme.typography.titleLarge)
                        Text("Версия $version", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsSection("Оформление") {
                    Column(Modifier.selectableGroup()) {
                        listOf(Triple("system", "Как на устройстве", Icons.Outlined.BrightnessAuto),
                            Triple("light", "Светлое", Icons.Outlined.LightMode),
                            Triple("dark", "Тёмное", Icons.Outlined.DarkMode)).forEach { (id, label, icon) ->
                            val selected = state.theme == id
                            Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton,
                                    onClick = { onTheme(id) }).padding(horizontal = 16.dp, vertical = 8.dp)
                                    .heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    RadioButton(selected, onClick = null)
                                }
                            }
                        }
                    }
                }
                SettingsSection("Учёба") {
                    SettingsAction(Icons.Outlined.School, "Сменить группу",
                        state.group?.let { "${it.name} · ${it.institute.shortName}" } ?: "Выбрать институт и группу",
                        onChooseGroup)
                }
                SettingsSection("Виджет расписания") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Widgets, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Пары на главном экране", style = MaterialTheme.typography.titleMedium)
                                Text("Один день, краткий список пар и переключение дат.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(onClick = onPinWidget, modifier = Modifier.fillMaxWidth()) { Text("Добавить виджет") }
                        Text("Если рабочий стол не поддерживает добавление кнопкой, покажем инструкцию.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsSection("О приложении") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Расписание доступно без интернета", style = MaterialTheme.typography.titleMedium)
                        Text("Загруженные недели сохраняются на устройстве. При подключении приложение проверяет обновления; фоновые проверки зависят от Android.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Неофициальное студенческое приложение. Источник — сайт КНИТУ. Время занятий — казанское (МСК).",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsAction(Icons.Outlined.Code, "Открыть проект на GitHub", "Исходный код и сообщения об ошибках",
                        { onOpenLink("https://github.com/wesdyqq/KNITUshka") })
                    SettingsAction(Icons.Outlined.Description, "Открыть лицензию MIT", "Свободное программное обеспечение",
                        { onOpenLink("https://github.com/wesdyqq/KNITUshka/blob/main/LICENSE") })
                }
                Text("© 2026 Камиль Сафьянов", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
