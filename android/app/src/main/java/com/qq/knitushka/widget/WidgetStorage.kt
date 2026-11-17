package com.qq.knitushka.widget

import android.content.Context
import com.qq.knitushka.data.Lesson
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class WidgetLesson(val number: Int, val subject: String, val time: String, val room: String)
data class WidgetSnapshot(val date: LocalDate, val group: String, val emptyMessage: String, val lessons: List<WidgetLesson>)

internal class WidgetStorage(context: Context) {
    private val preferences = context.getSharedPreferences("schedule_widgets", Context.MODE_PRIVATE)

    fun state(id: Int): WidgetDayState = WidgetDayState(
        preferences.getString("date:$id", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        preferences.getLong("changed:$id", 0L),
    )

    fun saveState(id: Int, state: WidgetDayState) {
        preferences.edit().putString("date:$id", state.date?.toString()).putLong("changed:$id", state.changedAt).commit()
    }

    fun saveSnapshot(id: Int, snapshot: WidgetSnapshot) {
        val rows = JSONArray()
        snapshot.lessons.forEach { row -> rows.put(JSONObject().put("number", row.number).put("subject", row.subject)
            .put("time", row.time).put("room", row.room)) }
        val json = JSONObject().put("date", snapshot.date.toString()).put("group", snapshot.group)
            .put("empty", snapshot.emptyMessage).put("lessons", rows)
        preferences.edit().putString("snapshot:$id", json.toString()).commit()
    }

    fun snapshot(id: Int): WidgetSnapshot? = runCatching {
        val json = JSONObject(preferences.getString("snapshot:$id", null) ?: return null)
        val rows = json.getJSONArray("lessons")
        WidgetSnapshot(LocalDate.parse(json.getString("date")), json.getString("group"), json.getString("empty"),
            (0 until rows.length()).map { index -> rows.getJSONObject(index).let {
                WidgetLesson(it.getInt("number"), it.getString("subject"), it.getString("time"), it.getString("room"))
            } })
    }.getOrNull()

    fun remove(id: Int) { preferences.edit().remove("date:$id").remove("changed:$id").remove("snapshot:$id").commit() }
}

internal fun Lesson.asWidgetLesson() = WidgetLesson(number, subject, "$start – $end", room)
