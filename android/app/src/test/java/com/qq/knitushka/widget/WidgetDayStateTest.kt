package com.qq.knitushka.widget

import com.qq.knitushka.data.kazanZone
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WidgetDayStateTest {
    private val day = LocalDate.of(2026, 11, 17)
    private val now = day.atTime(12, 0).atZone(kazanZone).toInstant().toEpochMilli()

    @Test fun `default follows today and seven day indicator can include sunday`() {
        assertEquals(day, WidgetDayState().selectedDate(now))
        assertEquals(7, WidgetDayState().move(5, now).date!!.dayOfWeek.value)
    }
    @Test fun `manual day survives until ten minutes and expires at the deadline`() {
        val state = WidgetDayState().move(1, now)
        assertEquals(day.plusDays(1), state.selectedDate(now + 599_999))
        assertEquals(day, state.selectedDate(now + 600_000))
        assertTrue(state.isExpired(now + 600_000))
    }
    @Test fun `subsequent navigation restarts the timer`() {
        val state = WidgetDayState().move(1, now).move(1, now + 300_000)
        assertEquals(day.plusDays(2), state.selectedDate(now + 600_000))
        assertEquals(now + 900_000, state.nextUpdateAt(now + 300_000))
    }
    @Test fun `return to today resumes automatic following`() {
        assertEquals(WidgetDayState(), WidgetDayState().move(1, now).move(-1, now + 1000))
    }
    @Test fun `midnight advances automatic date without saved manual date`() {
        val midnight = day.plusDays(1).atStartOfDay(kazanZone).toInstant().toEpochMilli()
        assertEquals(midnight, WidgetDayState().nextUpdateAt(now))
        assertEquals(day.plusDays(1), WidgetDayState().selectedDate(midnight))
    }
    @Test fun `expired state navigates relative to today instead of stale day`() {
        assertEquals(day.minusDays(1), WidgetDayState(day.plusDays(10), now).move(-1, now + 600_000).date)
    }
    @Test fun `clock moving backwards cannot pin a date forever`() {
        assertTrue(WidgetDayState(day.plusDays(1), now + 1000).isExpired(now))
        assertEquals(day, WidgetDayState(day.plusDays(1), now + 1000).selectedDate(now))
    }
}
