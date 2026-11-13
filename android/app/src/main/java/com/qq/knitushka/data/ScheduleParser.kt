package com.qq.knitushka.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.text.Normalizer
import java.util.Locale

val kazanZone: ZoneId = ZoneId.of("Europe/Moscow")
fun today(): LocalDate = LocalDate.now(kazanZone)
fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

data class Institute(val id: String, val shortName: String, val name: String)
data class StudyGroup(val id: String, val name: String, val institute: Institute)
data class Lesson(
    val date: LocalDate,
    val number: Int,
    val start: String,
    val end: String,
    val room: String,
    val subject: String,
    val type: String,
    val teachers: List<String>,
    val details: String,
)
data class ScheduleWeek(val monday: LocalDate, val parity: String, val lessons: List<Lesson>)
class SourceFormatException : Exception("Структура расписания на сайте не распознана")

/** Parses only the canonical weekly table, never the duplicated mobile tables. */
object ScheduleParser {
    private val datePattern = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")
    private val timePattern = Regex("(\\d{2}:\\d{2})\\s*[-–]\\s*(\\d{2}:\\d{2})")
    private val groupId = Regex("form2\\.g\\.value\\s*=\\s*['\"](\\d+)['\"]")
    private val instituteId = Regex("form3\\.f\\.value\\s*=\\s*['\"](\\d+)['\"]")
    private val typePattern = Regex("^(.*)\\s+\\((ЛЕКЦ|ПЗ|ЛР|ПР|СР|КОНС|ЭКЗ)[^)]*\\)$")

    fun institutes(html: String): List<Institute> = Jsoup.parse(html).select("[onclick]")
        .mapNotNull { e ->
            val id = instituteId.find(e.attr("onclick"))?.groupValues?.get(1) ?: return@mapNotNull null
            val short = e.selectFirst("b")?.text()?.trim().orEmpty()
            if (short.isEmpty()) return@mapNotNull null
            Institute(id, short, e.text().removePrefix(short).trim())
        }.distinctBy { it.id }.also { if (it.isEmpty()) throw SourceFormatException() }

    fun groups(html: String, institute: Institute): List<StudyGroup> = Jsoup.parse(html)
        .select("[onclick]").mapNotNull { e ->
            // Exam lists repeat groups; the regular timetable is the source for selection.
            if ("www_GEgrid" in e.attr("onclick")) return@mapNotNull null
            val id = groupId.find(e.attr("onclick"))?.groupValues?.get(1) ?: return@mapNotNull null
            StudyGroup(id, e.text().trim(), institute)
        }.distinctBy { it.id }.also { if (it.isEmpty()) throw SourceFormatException() }

    fun week(html: String, requested: LocalDate): ScheduleWeek {
        val doc = Jsoup.parse(html)
        val table = doc.select("table").firstOrNull { table ->
            val cells = table.select("tr").firstOrNull()?.children().orEmpty()
            cells.size >= 7 && cells.firstOrNull()?.text()?.contains("Время") == true &&
                cells.drop(1).take(6).all { datePattern.containsMatchIn(it.text()) }
        } ?: throw SourceFormatException()
        val rows = table.select("tr")
        val dates = rows.first()!!.children().drop(1).take(6).map {
            LocalDate.parse(datePattern.find(it.text())!!.value, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }
        val monday = requested.monday()
        if (dates != (0L..5L).map { monday.plusDays(it) }) throw SourceFormatException()
        var recognizedRows = 0
        val lessons = buildList {
            for (row in rows.drop(1)) {
                val cells = row.children()
                val times = timePattern.find(cells.firstOrNull()?.text().orEmpty()) ?: continue
                if (cells.size != 7) throw SourceFormatException()
                recognizedRows++
                val number = Regex("\\d+").find(cells[0].text())!!.value.toInt()
                cells.drop(1).forEachIndexed { day, cell ->
                    addAll(parseCell(cell, dates[day], number, times.groupValues[1], times.groupValues[2]))
                }
            }
        }
        if (recognizedRows == 0) throw SourceFormatException()
        val parity = Regex("(?<![а-яё])(?:нечетная|нечётная|четная|чётная) неделя", RegexOption.IGNORE_CASE)
            .find(doc.text())?.value.orEmpty().replaceFirstChar { it.uppercase() }
        return ScheduleWeek(monday, parity, removeDuplicates(lessons))
    }

    private val month = "(?:янв|фев|мар|апр|май|мая|июн|июл|авг|сен|окт|ноя|дек)[а-яё]*\\.?"
    private val period = Regex("\\d{1,2}\\s+$month\\s*[-–—]\\s*\\d{1,2}\\s+$month", RegexOption.IGNORE_CASE)

    /** A period is source metadata, not lesson identity. Missing fields may enrich one unambiguous match. */
    private fun removeDuplicates(lessons: List<Lesson>): List<Lesson> {
        val sameSlots = lessons.groupBy { lesson ->
            lesson.copy(number = 0, room = "", teachers = emptyList(), subject = identityText(lesson.subject),
                type = identityText(lesson.type), details = identityText(period.replace(lesson.details, "")).trim(' ', '·'))
        }
        return sameSlots.values.flatMap { slot ->
            val result = mutableListOf<Lesson>()
            // Resolve complete records first, so an unspecified room never joins two known rooms.
            for (lesson in slot.sortedByDescending { (if (it.room.isNotBlank()) 1 else 0) + (if (it.teachers.isNotEmpty()) 1 else 0) }) {
                val matches = result.indices.filter { i ->
                    val existing = result[i]
                    (existing.room.isBlank() || lesson.room.isBlank() || identityText(existing.room) == identityText(lesson.room)) &&
                        (existing.teachers.isEmpty() || lesson.teachers.isEmpty() || teacherKey(existing) == teacherKey(lesson))
                }
                if (matches.size != 1) {
                    result.add(lesson)
                    continue
                }
                val index = matches.single()
                val existing = result[index]
                result[index] = existing.copy(
                    room = existing.room.ifBlank { lesson.room },
                    teachers = existing.teachers.ifEmpty { lesson.teachers },
                    details = (existing.details.split(" · ") + lesson.details.split(" · "))
                        .filter { it.isNotBlank() }.distinctBy(::identityText).joinToString(" · "),
                )
            }
            result
        }
    }

    private fun teacherKey(lesson: Lesson) = lesson.teachers.map(::identityText).distinct().sorted()

    private fun identityText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")
        .replace(Regex("\\p{Pd}"), "-")
        .replace(Regex("[\\s\\p{Z}]+"), " ")
        .replace(Regex("\\.\\s+"), ".")
        .trim().lowercase(Locale.ROOT)

    private fun parseCell(cell: Element, date: LocalDate, number: Int, start: String, end: String): List<Lesson> {
        if (cell.text().isBlank()) return emptyList()
        // Each bold room begins a separate lesson. Teachers/notes belong to that block.
        val parts = cell.html().split(Regex("(?i)(?=<b(?:\\s[^>]*)?>)"))
            .filter { Jsoup.parseBodyFragment(it).text().isNotBlank() }
        return parts.map { part ->
            val fragment = Jsoup.parseBodyFragment(part)
            val room = fragment.selectFirst("b")?.text().orEmpty()
            fragment.select("b").remove()
            val teachers = fragment.select("a[href*=www_Pgrid]").map { it.text() }.filter { it.isNotBlank() }.distinct()
            fragment.select("a[href*=www_Pgrid]").remove()
            fragment.select("br").forEach { it.before("\n") }
            val lines = fragment.body().wholeText().split('\n').map { it.replace('\u00a0', ' ').trim() }
                .filter { it.isNotBlank() && it != "," }
            val title = lines.firstOrNull() ?: throw SourceFormatException()
            val match = typePattern.matchEntire(title)
            Lesson(date, number, start, end, room, match?.groupValues?.get(1)?.trim() ?: title,
                match?.groupValues?.get(2).orEmpty(), teachers, lines.drop(1).joinToString(" · "))
        }
    }
}
