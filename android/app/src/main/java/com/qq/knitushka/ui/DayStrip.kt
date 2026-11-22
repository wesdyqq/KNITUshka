package com.qq.knitushka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
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
    val leftArrow = remember { MutableTransitionState(direction == TodayArrow.LEFT) }
    val rightArrow = remember { MutableTransitionState(direction == TodayArrow.RIGHT) }
    leftArrow.targetState = direction == TodayArrow.LEFT
    rightArrow.targetState = direction == TodayArrow.RIGHT
    val leftVisible = leftArrow.currentState || leftArrow.targetState
    val rightVisible = rightArrow.currentState || rightArrow.targetState
    val selectedIndex = date.dayOfWeek.value - 1
    val row = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    val density = LocalDensity.current
    val leftClearance = with(density) { if (leftVisible) 44.dp.toPx() else 0f }
    val rightClearance = with(density) { if (rightVisible) 44.dp.toPx() else 0f }
    val edgeShift = remember { mutableFloatStateOf(0f) }
    // Clearance is extra travel after reaching an edge, NOT padding or a list item.
    // Showing an arrow changes only the allowed travel, never the current position.
    val clearanceScroll = remember(leftClearance, rightClearance) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val old = edgeShift.floatValue
                if (old == 0f) return Offset.Zero
                val target = if (old > 0f) (old + available.x).coerceIn(0f, leftClearance)
                    else (old + available.x).coerceIn(-rightClearance, 0f)
                edgeShift.floatValue = target
                return Offset(target - old, 0f)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val old = edgeShift.floatValue
                val target = (old + available.x).coerceIn(-rightClearance, leftClearance)
                edgeShift.floatValue = target
                return Offset(target - old, 0f)
            }
        }
    }
    LaunchedEffect(leftClearance, rightClearance) {
        edgeShift.floatValue = edgeShift.floatValue.coerceIn(-rightClearance, leftClearance)
    }
    val ru = Locale.forLanguageTag("ru-RU")
    LaunchedEffect(date) {
        // Selecting a visible day must not reposition the strip either. Scroll only
        // when navigation actually selects a day outside the visible viewport.
        val visible = row.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty() && visible.none { it.index == selectedIndex }) {
            row.animateScrollToItem((selectedIndex - 3).coerceAtLeast(0))
        }
    }
    Box(Modifier.fillMaxWidth().clipToBounds().testTag("day_strip")) {
        LazyRow(state = row, modifier = Modifier.nestedScroll(clearanceScroll)
            .graphicsLayer { translationX = edgeShift.floatValue },
            contentPadding = PaddingValues(horizontal = 16.dp), overscrollEffect = null,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items((0L..6L).map { date.monday().plusDays(it) }, key = { it.toString() }) { day ->
                val hasLessons = day in lessonDates
                Surface(
                    modifier = Modifier.widthIn(min = 48.dp).testTag("day_$day")
                        .selectable(day == date, onClick = { onSelectDate(day) }, role = Role.Tab)
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
        // Separate, fixed overlays prevent the exiting left arrow from jumping to the
        // right when selection reaches today (or crosses it during fast navigation).
        for (side in listOf(TodayArrow.LEFT, TodayArrow.RIGHT)) {
            AnimatedVisibility(visibleState = if (side == TodayArrow.LEFT) leftArrow else rightArrow,
                modifier = Modifier.align(if (side == TodayArrow.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 8.dp),
                enter = fadeIn(tween(160)), exit = fadeOut(tween(125))) {
                Surface(onClick = onToday, modifier = Modifier.size(44.dp)
                    .testTag(if (side == TodayArrow.LEFT) "today_arrow_left" else "today_arrow_right"),
                    shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary, shadowElevation = 4.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (side == TodayArrow.LEFT) Icons.AutoMirrored.Outlined.ArrowBack else Icons.AutoMirrored.Outlined.ArrowForward,
                            "Вернуться к сегодняшнему дню", Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
