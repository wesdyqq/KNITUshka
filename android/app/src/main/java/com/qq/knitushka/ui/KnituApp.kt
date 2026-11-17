package com.qq.knitushka.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qq.knitushka.ScheduleUiState
import com.qq.knitushka.R
import com.qq.knitushka.ScheduleViewModel
import com.qq.knitushka.data.*
import com.qq.knitushka.widget.ScheduleWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru = Locale.forLanguageTag("ru-RU")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", ru)

@Composable
fun KnituApp(state: ScheduleUiState, model: ScheduleViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when {
                state.starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.choosing -> GroupPicker(state, model)
                else -> Timetable(state, model)
            }
        }
    }
}

@Composable
private fun RabbitMark(modifier: Modifier = Modifier) {
    Image(painter = painterResource(R.drawable.knitushka_mark), contentDescription = null, modifier = modifier)
}

@Composable
private fun GroupPicker(state: ScheduleUiState, model: ScheduleViewModel) {
    var query by rememberSaveable(state.institute?.id) { mutableStateOf("") }
    BackHandler(state.group != null || state.institute != null) {
        if (state.institute != null && !state.pickerLoading) model.chooseInstitute(null) else model.cancelChoice()
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.institute != null || state.group != null) {
                IconButton(onClick = { if (state.institute != null) model.chooseInstitute(null) else model.cancelChoice() }, enabled = !state.pickerLoading) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Вернуться назад")
                }
            }
            Spacer(Modifier.weight(1f))
            Text(if (state.institute == null) "ШАГ 1 ИЗ 2" else "ШАГ 2 ИЗ 2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(top = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (state.institute == null) "Привет, это\nКНИТУшка" else "Найдём твою\nгруппу", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                    Text(if (state.institute == null) "Расписание рядом. Даже без интернета.\nДля начала выбери институт." else "${state.institute.shortName} · Выбери группу — и расписание будет под рукой.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.institute != null) item {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Номер группы") }, placeholder = { Text("Например, 141-12") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(8.dp))
            }
            if (state.pickerLoading) item {
                Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Загружаем данные с сайта КНИТУ…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.pickerError != null) item {
                ErrorNotice(state.pickerError, retry = {
                    state.institute?.let(model::chooseInstitute) ?: model.loadInstitutes()
                })
            }
            if (!state.pickerLoading && state.institute == null) items(state.institutes, key = { it.id }) { institute ->
                ChoiceCard(institute.shortName, institute.name, { model.chooseInstitute(institute) })
            }
            if (!state.pickerLoading && state.institute != null) {
                val filtered = state.groups.filter { it.name.contains(query.trim(), ignoreCase = true) }
                items(filtered, key = { it.id }) { group ->
                    ChoiceCard(group.name, state.institute.shortName, { model.chooseGroup(group) })
                }
                if (filtered.isEmpty() && state.pickerError == null) item {
                    Column(Modifier.padding(vertical = 16.dp)) {
                        Text("Группа не найдена", style = MaterialTheme.typography.titleMedium)
                        Text("Проверь номер или выбери другой институт.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { query = ""; model.chooseInstitute(null) }) { Text("Выбрать институт") }
                    }
                }
            }
            if (state.institute == null) item {
                Text("Первую загрузку нужно выполнить с интернетом. Затем расписание сохранится на устройстве.", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Timetable(state: ScheduleUiState, model: ScheduleViewModel) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var pinRequested by remember { mutableStateOf(false) }
    var manualWidgetHelp by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(pinRequested) {
        if (pinRequested) {
            // Let Compose remove the settings dialog window before asking the launcher for UI.
            withFrameNanos { }
            withFrameNanos { }
            val accepted = runCatching { ScheduleWidgetProvider.requestPin(context) }.getOrDefault(false)
            if (accepted) android.widget.Toast.makeText(context,
                "Запрос отправлен. Если окна нет, добавь виджет через меню рабочего стола.",
                android.widget.Toast.LENGTH_LONG).show()
            else manualWidgetHelp = true
            pinRequested = false
        }
    }
    var currentToday by remember { mutableStateOf(today()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { while (true) { currentToday = today(); delay(30_000) } }
    val pages = remember { CalendarPages(state.date) }
    val pager = rememberPagerState(initialPage = pages.dayPage(state.date), pageCount = { pages.pageCount })
    LaunchedEffect(state.date) {
        val target = pages.dayPage(state.date)
        if (pager.settledPage != target) {
            // Adjacent day buttons animate too; a week jump is handled by the separate week pager.
            if (kotlin.math.abs(pager.settledPage - target) == 1) pager.animateScrollToPage(target)
            else pager.scrollToPage(target)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect { page ->
            model.selectDate(pages.dayAt(page))
        }
    }
    // One vertical scroll owner for the whole screen. The pager owns only the horizontal axis.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TimetableHeader(state, model::choose, model::selectDate, { settings = true }, Modifier.fillMaxWidth(),
                todayDate = currentToday, onToday = {
                    scope.launch { pager.animateScrollToPage(pages.dayPage(currentToday)) }
                })
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 1, key = { pages.dayAt(it).toString() }) { page ->
                val date = pages.dayAt(page)
                val cached = if (state.cached?.week?.monday == date.monday()) state.cached else state.previewWeeks[date.monday()]
                DayLessons(state.copy(date = date, cached = cached,
                    error = if (date.monday() == state.date.monday()) state.error else null), model)
            }
    }
    if (settings) SettingsDialog(state, model::setTheme, { settings = false; model.choose() }, onDismiss = { settings = false },
        onPinWidget = { settings = false; pinRequested = true })
    if (manualWidgetHelp) AlertDialog(onDismissRequest = { manualWidgetHelp = false },
        title = { Text("Добавление через рабочий стол") },
        text = { Text("Этот рабочий стол не поддерживает добавление виджета кнопкой приложения.\n\n1. Зажми свободное место на главном экране.\n2. Открой «Виджеты» (или «Компоненты» → «Виджеты Android»).\n3. Найди «КНИТУшка» и перетащи виджет на экран.") },
        confirmButton = { TextButton(onClick = {
            manualWidgetHelp = false
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME))
        }) { Text("Открыть главный экран") } },
        dismissButton = { TextButton(onClick = { manualWidgetHelp = false }) { Text("Закрыть") } })
}

@Composable
internal fun TimetableHeader(state: ScheduleUiState, onChooseGroup: () -> Unit,
    onSelectDate: (LocalDate) -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier,
    todayDate: LocalDate = today(), onToday: () -> Unit = { onSelectDate(todayDate) }) {
    val group = state.group ?: return
    Column(modifier.testTag("schedule_header").padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Расписание", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("КНИТУшка", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Открыть настройки") }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Surface(onClick = onChooseGroup, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.School, null, Modifier.size(18.dp))
                        Text("${group.name} · ${group.institute.shortName}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.UnfoldMore, "Сменить группу", Modifier.size(18.dp))
                    }
                }
            }
            WeekPager(state, onSelectDate)
            DayStrip(state.date, todayDate, state.cached?.week?.lessons?.map { it.date }?.toSet().orEmpty(), onSelectDate, onToday)
    }
}

@Composable
private fun DayLessons(state: ScheduleUiState, model: ScheduleViewModel) {
    var now by remember { mutableStateOf(ZonedDateTime.now(kazanZone)) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(kazanZone); delay(30_000) } }
    val group = state.group ?: return
    val lessons = state.cached?.week?.lessons?.filter { it.date == state.date }.orEmpty()
        .sortedWith(compareBy({ it.start }, { it.number }))
    val current = lessons.firstOrNull { state.date == now.toLocalDate() && now.toLocalTime() >= LocalTime.parse(it.start) && now.toLocalTime() < LocalTime.parse(it.end) }
    val next = lessons.firstOrNull { state.date == now.toLocalDate() && now.toLocalTime() < LocalTime.parse(it.start) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.cached != null && (current != null || next != null)) {
            val lesson = current ?: next!!
            Surface(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (current != null) "СЕЙЧАС · ДО ${lesson.end}" else "ДАЛЬШЕ · В ${lesson.start}", style = MaterialTheme.typography.labelSmall)
                    Text(lesson.subject, style = MaterialTheme.typography.titleMedium)
                    if (lesson.room.isNotBlank()) Text(lesson.room, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.error != null) { Box(Modifier.padding(horizontal = 24.dp)) { ErrorNotice(state.error, retry = { model.loadWeek(true) }) } }
        if (state.cached == null && !state.loading && state.error == null) {
            EmptyDay("Эта неделя ещё не сохранена", "Подключись к интернету и загрузи расписание.", "Загрузить неделю", { model.loadWeek(true) })
        }
        if (state.cached != null && lessons.isEmpty()) {
            EmptyDay("На этот день пар нет", "В сохранённом расписании занятий не найдено. Можно заглянуть в другой день.", "Открыть следующий день", { model.selectDate(state.date.plusDays(1)) })
        }
        lessons.forEach { lesson -> LessonCard(lesson, lesson.date == now.toLocalDate() && now.toLocalTime() >= LocalTime.parse(lesson.start) && now.toLocalTime() < LocalTime.parse(lesson.end)) }
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(if (state.error != null) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    val checked = state.cached?.checkedAt?.let { Instant.ofEpochMilli(it).atZone(kazanZone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", ru)) }
                    Text(if (state.loading) "Проверяем расписание…" else if (checked != null) "Проверено $checked${if (state.error != null) " · сохранённая версия" else ""}" else "Неделя не сохранена",
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { model.loadWeek(true) }, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "Обновить расписание") }
                }
                val uri = LocalUriHandler.current
                TextButton(onClick = { uri.openUri("https://www.kstu.ru/www_Ggrid.jsp?f=${group.institute.id}&g=${group.id}&d=${state.date}") }) {
                    Text("Открыть расписание на сайте КНИТУ", style = MaterialTheme.typography.labelSmall)
                }
            }
    }
}

@Composable
private fun WeekPager(state: ScheduleUiState, onSelectDate: (LocalDate) -> Unit) {
    val pages = remember { CalendarPages(state.date) }
    val pager = rememberPagerState(initialPage = pages.weekPage(state.date), pageCount = { pages.pageCount })
    val scope = rememberCoroutineScope()
    val latestSelected by rememberUpdatedState(state.date)
    val select by rememberUpdatedState(onSelectDate)
    val desired = state.date
    LaunchedEffect(desired.monday()) {
        val target = pages.weekPage(desired)
        if (pager.settledPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect { page ->
            val monday = pages.weekAt(page)
            if (monday != latestSelected.monday()) {
                select(monday.plusDays((latestSelected.dayOfWeek.value - 1).toLong()))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.settledPage - 1) } }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Предыдущая неделя")
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            val monday = pages.weekAt(page)
            val week = if (state.cached?.week?.monday == monday) state.cached else state.previewWeeks[monday]
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("${monday.format(shortDate)} — ${monday.plusDays(6).format(shortDate)}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                Text(week?.week?.parity?.ifBlank { "Неделя" } ?: "Неделя", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.settledPage + 1) } }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Следующая неделя")
        }
    }
}

@Composable
internal fun LessonCard(lesson: Lesson, active: Boolean) {
    Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth().then(if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        SelectionContainer {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.widthIn(min = 60.dp, max = 84.dp).testTag("lesson_time_rail"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                    Surface(shape = CircleShape, color = KnituRed,
                        contentColor = KnituBeige,
                        modifier = Modifier.size(maxOf(44f, 28f * fontScale + 16f).dp).testTag("lesson_number_circle")) {
                        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(lesson.number.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                modifier = Modifier.semantics { contentDescription = "${lesson.number} пара" })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${lesson.start} –", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { contentDescription = "Начало занятия ${lesson.start}" })
                    Text(lesson.end, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { contentDescription = "Окончание занятия ${lesson.end}" })
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (active) Text("Идёт сейчас", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(lesson.subject, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 31.sp))
                    if (lesson.type.isNotBlank()) Text(when (lesson.type) { "ЛЕКЦ" -> "Лекция"; "ЛР" -> "Лабораторная работа"; "ПЗ", "ПР" -> "Практическое занятие"; else -> lesson.type }, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lesson.room.isNotBlank()) Metadata(Icons.Outlined.LocationOn, lesson.room)
                    if (lesson.teachers.isNotEmpty()) Metadata(Icons.Outlined.PersonOutline, lesson.teachers.joinToString(", "))
                    if (lesson.details.isNotBlank()) Text(lesson.details, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Metadata(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyDay(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun ErrorNotice(message: String, retry: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = retry, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("Повторить загрузку") }
        }
    }
}
