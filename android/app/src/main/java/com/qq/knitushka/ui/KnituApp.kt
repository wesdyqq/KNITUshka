package com.qq.knitushka.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qq.knitushka.ScheduleUiState
import com.qq.knitushka.ScheduleViewModel
import com.qq.knitushka.data.*
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

/** Original compact rabbit mark, not a reproduction of the supplied poster artwork. */
@Composable
private fun RabbitMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val accent = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier.background(bg, RoundedCornerShape(24.dp)).padding(12.dp)) {
        val w = size.width; val h = size.height
        rotate(-16f, Offset(w * .29f, h * .4f)) {
            drawOval(ink, Offset(w * .13f, 0f), Size(w * .26f, h * .66f))
            drawOval(bg, Offset(w * .20f, h * .08f), Size(w * .12f, h * .46f))
        }
        rotate(16f, Offset(w * .7f, h * .4f)) {
            drawOval(ink, Offset(w * .61f, 0f), Size(w * .26f, h * .66f))
            drawOval(bg, Offset(w * .68f, h * .08f), Size(w * .12f, h * .46f))
        }
        drawOval(bg, Offset(w * .09f, h * .36f), Size(w * .82f, h * .61f))
        drawOval(ink, Offset(w * .09f, h * .36f), Size(w * .82f, h * .61f), style = Stroke(w * .045f))
        drawCircle(ink, w * .035f, Offset(w * .34f, h * .61f))
        drawCircle(ink, w * .035f, Offset(w * .66f, h * .61f))
        drawOval(accent, Offset(w * .44f, h * .69f), Size(w * .12f, h * .075f))
        drawArc(ink, 15f, 150f, false, Offset(w * .36f, h * .72f), Size(w * .28f, h * .13f), style = Stroke(w * .025f))
    }
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
            TimetableHeader(state, model, { settings = true }, Modifier.fillMaxWidth())
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 1, key = { pages.dayAt(it).toString() }) { page ->
                val date = pages.dayAt(page)
                val cached = if (state.cached?.week?.monday == date.monday()) state.cached else state.previewWeeks[date.monday()]
                DayLessons(state.copy(date = date, cached = cached,
                    error = if (date.monday() == state.date.monday()) state.error else null), model)
            }
    }
    if (settings) SettingsDialog(state, model, onDismiss = { settings = false })
}

@Composable
private fun TimetableHeader(state: ScheduleUiState, model: ScheduleViewModel, onSettings: () -> Unit, modifier: Modifier) {
    val group = state.group ?: return
    Column(modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("КНИТУшка", style = MaterialTheme.typography.titleLarge)
                    Text("В ритме твоих пар", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Открыть настройки") }
            }
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(8.dp))
                Text("Расписание", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Surface(onClick = model::choose, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.School, null, Modifier.size(20.dp))
                        Text("${group.name} · ${group.institute.shortName}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.UnfoldMore, "Сменить группу", Modifier.size(20.dp))
                    }
                }
            }
            WeekPager(state, model::selectDate)
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((0L..6L).map { state.date.monday().plusDays(it) }, key = { it.toString() }) { day ->
                    val selected = day == state.date
                    val hasLessons = state.cached?.week?.lessons?.any { it.date == day } == true
                    Surface(
                        modifier = Modifier.widthIn(min = 54.dp).selectable(selected, onClick = { model.selectDate(day) }, role = Role.Tab)
                            .semantics { contentDescription = "${day.format(dayFormat)}${if (hasLessons) ", есть занятия" else ""}" },
                        shape = RoundedCornerShape(18.dp), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(day.format(DateTimeFormatter.ofPattern("EE", ru)), style = MaterialTheme.typography.labelSmall)
                            Text(day.dayOfMonth.toString(), fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                            Box(Modifier.size(4.dp).background(if (hasLessons) LocalContentColor.current else Color.Transparent, CircleShape))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.date.format(dayFormat).replaceFirstChar { it.uppercase(ru) }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { model.selectDate(today()) }) { Text("Сегодня") }
            }
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.settledPage - 1) } }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Предыдущая неделя")
        }
        HorizontalPager(pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            val monday = pages.weekAt(page)
            val week = if (state.cached?.week?.monday == monday) state.cached else state.previewWeeks[monday]
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${monday.format(shortDate)} — ${monday.plusDays(6).format(shortDate)}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(week?.week?.parity?.ifBlank { "Неделя" } ?: "Неделя", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(monday.year.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.settledPage + 1) } }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Следующая неделя")
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, active: Boolean) {
    Surface(Modifier.padding(horizontal = 24.dp).fillMaxWidth().then(if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        SelectionContainer {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${lesson.start} — ${lesson.end}", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f))
                    Text("${lesson.number} пара", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (active) Text("Идёт сейчас", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(lesson.subject, style = MaterialTheme.typography.titleMedium)
                if (lesson.type.isNotBlank()) Text(when (lesson.type) { "ЛЕКЦ" -> "Лекция"; "ЛР" -> "Лабораторная работа"; "ПЗ", "ПР" -> "Практическое занятие"; else -> lesson.type }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lesson.room.isNotBlank()) Metadata(Icons.Outlined.LocationOn, lesson.room)
                if (lesson.teachers.isNotEmpty()) Metadata(Icons.Outlined.PersonOutline, lesson.teachers.joinToString(", "))
                if (lesson.details.isNotBlank()) Text(lesson.details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Metadata(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun SettingsDialog(state: ScheduleUiState, model: ScheduleViewModel, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Настройки") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Оформление", style = MaterialTheme.typography.titleMedium)
            listOf("system" to "Как на устройстве", "light" to "Светлое", "dark" to "Тёмное").forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().selectable(state.theme == id, onClick = { model.setTheme(id) }, role = Role.RadioButton).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.theme == id, onClick = null)
                    Text(label, Modifier.padding(start = 12.dp))
                }
            }
            OutlinedButton(onClick = { onDismiss(); model.choose() }, modifier = Modifier.fillMaxWidth()) { Text("Сменить группу") }
            Text("Сохранённые недели доступны без интернета. При подключении приложение проверяет обновления. Фоновые проверки выполняются по возможности Android.", style = MaterialTheme.typography.bodyMedium)
            Text("Неофициальное студенческое приложение. Источник расписания — сайт КНИТУ. Время занятий — казанское (МСК).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("О приложении", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("КНИТУшка — приложение с открытым исходным кодом. Код распространяется по лицензии MIT.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { uri.openUri("https://github.com/wesdyqq/KNITUshka") }) {
                Text("Оригинальный проект на GitHub")
            }
            TextButton(onClick = { uri.openUri("https://github.com/wesdyqq/KNITUshka/blob/main/LICENSE") }) {
                Text("Открыть лицензию MIT")
            }
            Text("© 2026 Камиль Сафьянов", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
}
