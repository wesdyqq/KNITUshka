package com.qq.knitushka.widget

import com.qq.knitushka.data.kazanZone
import java.time.Instant
import java.time.LocalDate

/** No date means follow today, including midnight and process restarts. */
data class WidgetDayState(val date: LocalDate? = null, val changedAt: Long = 0L) {
    fun selectedDate(now: Long): LocalDate {
        val today = Instant.ofEpochMilli(now).atZone(kazanZone).toLocalDate()
        return if (date != null && changedAt <= now && now - changedAt < RESET_AFTER_MILLIS) date else today
    }

    fun isExpired(now: Long): Boolean = date != null && (now < changedAt || now - changedAt >= RESET_AFTER_MILLIS)

    fun move(days: Long, now: Long): WidgetDayState {
        val next = selectedDate(now).plusDays(days)
        val today = Instant.ofEpochMilli(now).atZone(kazanZone).toLocalDate()
        return if (next == today) WidgetDayState() else WidgetDayState(next, now)
    }

    fun nextUpdateAt(now: Long): Long {
        val midnight = Instant.ofEpochMilli(now).atZone(kazanZone).toLocalDate().plusDays(1)
            .atStartOfDay(kazanZone).toInstant().toEpochMilli()
        return if (date != null && !isExpired(now)) minOf(midnight, changedAt + RESET_AFTER_MILLIS) else midnight
    }

    companion object { const val RESET_AFTER_MILLIS = 10 * 60_000L }
}
