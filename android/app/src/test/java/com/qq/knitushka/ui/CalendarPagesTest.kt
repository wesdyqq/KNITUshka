package com.qq.knitushka.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarPagesTest {
    private val anchor = LocalDate.of(2026, 11, 13)
    private val pages = CalendarPages(anchor)

    @Test fun `adjacent day pages cross week and year boundaries`() {
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(sunday.plusDays(1), pages.dayAt(pages.dayPage(sunday) + 1))
        val yearEnd = LocalDate.of(2026, 12, 31)
        assertEquals(LocalDate.of(2027, 1, 1), pages.dayAt(pages.dayPage(yearEnd) + 1))
    }

    @Test fun `dates map back to the same day page`() {
        for (offset in -14L..14L) {
            val date = anchor.plusDays(offset)
            assertEquals(date, pages.dayAt(pages.dayPage(date)))
        }
    }

    @Test fun `week pages always start on monday`() {
        assertEquals(LocalDate.of(2026, 11, 9), pages.weekAt(pages.initialPage))
        assertEquals(LocalDate.of(2026, 11, 16), pages.weekAt(pages.initialPage + 1))
        assertEquals(pages.initialPage, pages.weekPage(LocalDate.of(2026, 11, 15)))
    }
}
