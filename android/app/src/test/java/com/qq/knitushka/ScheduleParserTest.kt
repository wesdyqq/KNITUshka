package com.qq.knitushka

import com.qq.knitushka.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleParserTest {
    private val date = LocalDate.of(2026, 11, 11)
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    @Test fun `live source snapshot has correct week without mobile duplicates`() {
        val week = ScheduleParser.week(fixture("week.html"), date)
        assertEquals(LocalDate.of(2026, 11, 9), week.monday)
        assertEquals("Нечетная неделя", week.parity)
        val tuesday = week.lessons.filter { it.date == LocalDate.of(2026, 11, 10) }
        assertEquals(5, tuesday.size)
        val design = tuesday.filter { it.number == 4 }
        assertEquals(listOf("Д-405", "Д-407"), design.map { it.room })
        assertEquals(listOf("Хамидуллина Д.А."), design[0].teachers)
        assertEquals("Основы проектирования", design[0].subject)
        assertTrue(tuesday.filter { it.number == 5 }.all { "Курсовой проект" in it.details })
        assertEquals(2, tuesday.first().teachers.size)
    }

    @Test fun `institutes and groups are extracted from source actions`() {
        val html = fixture("institutes.html")
        val institutes = ScheduleParser.institutes(html)
        assertTrue(institutes.any { it.id == "320" && it.shortName == "ИУАИТ" })
        val groups = ScheduleParser.groups(html, institutes.first { it.id == "210" })
        assertTrue(groups.any { it.id == "47494" && it.name == "141-12" })
        assertEquals(groups.size, groups.map { it.id }.distinct().size)
    }

    private fun table(cell: String = "") = """
        <p>четная неделя</p><table><tr><td>Время</td>
        ${(9..14).joinToString("") { "<td>$it.11.2026</td>" }.replace("9.11", "09.11")}</tr>
        <tr><td>1 пара 08:00-09:30</td><td>$cell</td>${"<td><b></b><br></td>".repeat(5)}</tr></table>
    """.trimIndent()

    @Test fun `valid empty week is not a parse error`() {
        assertTrue(ScheduleParser.week(table(), date).lessons.isEmpty())
    }

    @Test fun `missing teacher and unknown type preserve lesson`() {
        val lesson = ScheduleParser.week(table("<b>Б-322</b><br>Военная подготовка (ЛЕКЦ)<br>1 сен - 31 дек"), date).lessons.single()
        assertEquals("Военная подготовка", lesson.subject)
        assertEquals("ЛЕКЦ", lesson.type)
        assertTrue(lesson.teachers.isEmpty())
    }

    @Test(expected = SourceFormatException::class)
    fun `server error is not an empty week`() { ScheduleParser.week("<html>Сервис недоступен</html>", date) }

    @Test(expected = SourceFormatException::class)
    fun `wrong week cannot replace requested cache`() { ScheduleParser.week(table(), date.plusWeeks(1)) }

    @Test fun `sunday belongs to previous monday`() {
        assertEquals(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15).monday())
    }

    private fun lesson(room: String = "Б-322", subject: String = "Математика", type: String = "ЛЕКЦ", teacher: String = "Иванов И.И.") =
        "<b>$room</b><br>$subject ($type)<br>1 сен - 31 дек<br><a href='/www_Pgrid.jsp?p=1'>$teacher</a>"

    @Test fun `identical lessons at the same time are shown once`() {
        assertEquals(1, ScheduleParser.week(table(lesson() + "<br>" + lesson()), date).lessons.size)
    }

    @Test fun `duplicate formatting differences do not create extra lessons`() {
        val html = table(lesson(room = "Б–322", subject = "Высшая  математика", teacher = "Иванов И. И.") + "<br>" +
            lesson(room = "Б-322", subject = "Высшая&nbsp;математика", teacher = "Иванов И.И."))
        assertEquals(1, ScheduleParser.week(html, date).lessons.size)
    }

    @Test fun `consecutive identical subjects stay separate`() {
        val secondRow = "<tr><td>2 пара 09:40-11:10</td><td>${lesson()}</td>${"<td></td>".repeat(5)}</tr>"
        val html = table(lesson()).replace("</table>", "$secondRow</table>")
        val lessons = ScheduleParser.week(html, date).lessons
        assertEquals(2, lessons.size)
        assertEquals(listOf("08:00", "09:40"), lessons.map { it.start })
    }

    @Test fun `different rooms teachers and lesson types remain separate`() {
        val html = table(listOf(lesson(), lesson(room = "Б-323"), lesson(teacher = "Петров П.П."), lesson(type = "ПЗ")).joinToString("<br>"))
        assertEquals(4, ScheduleParser.week(html, date).lessons.size)
    }

    @Test fun `same time on different days is not a duplicate`() {
        val html = table(lesson()).replace("<td><b></b><br></td>", "<td>${lesson()}</td>")
        assertEquals(6, ScheduleParser.week(html, date).lessons.size)
    }

    @Test fun `subgroup notes are not discarded`() {
        val html = table(lesson() + "<br>Подгруппа 1<br>" + lesson() + "<br>Подгруппа 2")
        assertEquals(2, ScheduleParser.week(html, date).lessons.size)
    }

    @Test fun `different source periods do not duplicate a lesson`() {
        val html = table(lesson() + "<br>" + lesson().replace("31 дек", "30 дек"))
        val result = ScheduleParser.week(html, date).lessons
        assertEquals(1, result.size)
        assertTrue(result.single().details.contains("30 дек"))
        assertTrue(result.single().details.contains("31 дек"))
    }

    @Test fun `missing teacher and room are filled from an unambiguous duplicate`() {
        val html = table(lesson(room = "", teacher = "") + "<br>" + lesson())
        val result = ScheduleParser.week(html, date).lessons.single()
        assertEquals("Б-322", result.room)
        assertEquals(listOf("Иванов И.И."), result.teachers)
    }

    @Test fun `unspecified room does not bridge two different known rooms`() {
        val html = table(lesson(room = "") + "<br>" + lesson(room = "Б-322") + "<br>" + lesson(room = "Б-323"))
        val result = ScheduleParser.week(html, date).lessons
        assertEquals(3, result.size)
        assertTrue(result.any { it.room == "Б-322" })
        assertTrue(result.any { it.room == "Б-323" })
    }

    @Test fun `861-72 source duplicates are merged but consecutive lessons remain`() {
        val result = ScheduleParser.week(fixture("week-861-72.html"), date).lessons
        val thursday = result.filter { it.date == LocalDate.of(2026, 11, 12) }
        assertEquals(3, thursday.size)
        assertEquals(1, thursday.count { it.subject == "Информатика" })
        val safety = thursday.single { it.subject == "Безопасность жизнедеятельности" }
        assertEquals("А-Круглый зал", safety.room)
        assertEquals(listOf("Латыпова Л.И."), safety.teachers)
        val history = result.filter { it.date == LocalDate.of(2026, 11, 14) && it.subject == "История России" && it.type == "ЛЕКЦ" }
        assertEquals(2, history.size)
        assertEquals(listOf("08:00", "09:40"), history.map { it.start })
        assertTrue(history.all { it.teachers == listOf("Коршунова О.Н.") })
        val government = result.single { it.date == LocalDate.of(2026, 11, 11) && it.subject == "Основы российской государственности" }
        assertEquals(listOf("Тузиков А.Р."), government.teachers)
    }
}
