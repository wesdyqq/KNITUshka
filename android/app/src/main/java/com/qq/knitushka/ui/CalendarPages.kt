package com.qq.knitushka.ui

import com.qq.knitushka.data.monday
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Stable date-to-page mapping, shared by gesture previews and button navigation. */
internal class CalendarPages(private val anchor: LocalDate) {
    val initialPage = 10_000
    val pageCount = 20_001
    fun dayAt(page: Int): LocalDate = anchor.plusDays((page - initialPage).toLong())
    fun dayPage(date: LocalDate): Int = initialPage + ChronoUnit.DAYS.between(anchor, date).toInt()
    fun weekAt(page: Int): LocalDate = anchor.monday().plusWeeks((page - initialPage).toLong())
    fun weekPage(date: LocalDate): Int = initialPage + ChronoUnit.WEEKS.between(anchor.monday(), date.monday()).toInt()
}
