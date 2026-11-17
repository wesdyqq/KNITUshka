package com.qq.knitushka.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayArrowTest {
    @Test fun `arrow is hidden today and points towards today across weeks and years`() {
        val today = LocalDate.of(2027, 1, 1)
        assertEquals(TodayArrow.HIDDEN, todayArrow(today, today))
        assertEquals(TodayArrow.RIGHT, todayArrow(LocalDate.of(2026, 12, 31), today))
        assertEquals(TodayArrow.RIGHT, todayArrow(today.minusWeeks(5), today))
        assertEquals(TodayArrow.LEFT, todayArrow(today.plusWeeks(5), today))
    }
}
