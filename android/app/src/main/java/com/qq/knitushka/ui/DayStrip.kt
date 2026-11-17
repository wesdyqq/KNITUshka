package com.qq.knitushka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qq.knitushka.data.monday
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class TodayArrow { HIDDEN, LEFT, RIGHT }
internal fun todayArrow(selected: LocalDate, today: LocalDate): TodayArrow = when {
    selected < today -> TodayArrow.RIGHT
    selected > today -> TodayArrow.LEFT
    else -> TodayArrow.HIDDEN
}

@Composable
internal fun DayStrip(date: LocalDate, today: LocalDate, lessonDates: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit) {
    val direction = todayArrow(date, today)
    val leading = direction == TodayArrow.LEFT
    val selectedIndex = date.dayOfWeek.value - 1
    val row = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    val ru = Locale.forLanguageTag("ru-RU")
    LaunchedEffect(date) { row.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0)) }
    Box(Modifier.fillMaxWidth().testTag("day_strip")) {
        LazyRow(state = row, contentPadding = PaddingValues(
            start = if (leading) 60.dp else 16.dp,
            end = if (direction == TodayArrow.RIGHT) 60.dp else 16.dp,
        ), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items((0L..6L).map { date.monday().plusDays(it) }, key = { it.toString() }) { day ->
                val hasLessons = day in lessonDates
                Surface(
                    modifier = Modifier.widthIn(min = 48.dp).selectable(day == date, onClick = { onSelectDate(day) }, role = Role.Tab)
                        .semantics { contentDescription = "${day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ru))}${if (hasLessons) ", есть занятия" else ""}" },
                    shape = RoundedCornerShape(18.dp),
                    color = if (day == date) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (day == date) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.format(DateTimeFormatter.ofPattern("EE", ru)), style = MaterialTheme.typography.labelSmall)
                        Text(day.dayOfMonth.toString(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Box(Modifier.size(4.dp).background(if (hasLessons) LocalContentColor.current else Color.Transparent, CircleShape))
                    }
                }
            }
        }
        AnimatedVisibility(visible = direction != TodayArrow.HIDDEN,
            modifier = Modifier.align(if (leading) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 8.dp),
            enter = fadeIn(), exit = fadeOut()) {
            Surface(onClick = onToday, modifier = Modifier.size(44.dp)
                .testTag(if (leading) "today_arrow_left" else "today_arrow_right"),
                shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary, shadowElevation = 4.dp) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (leading) Icons.AutoMirrored.Outlined.ArrowBack else Icons.AutoMirrored.Outlined.ArrowForward,
                        "Вернуться к сегодняшнему дню", Modifier.size(22.dp))
                }
            }
        }
    }
}
