package com.qq.knitushka.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

private val Context.preferences by preferencesDataStore("knitushka")
data class CachedWeek(val week: ScheduleWeek, val checkedAt: Long)

class ScheduleRepository(private val context: Context, private val onScheduleChanged: () -> Unit = {}) {
    private val database = Room.databaseBuilder(context, KnituDatabase::class.java, "knitushka.db").build()
    private val cache = database.cache()
    private val gate = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    private val selectedKey = stringPreferencesKey("selected_group")
    private val themeKey = stringPreferencesKey("theme")

    suspend fun selection(): StudyGroup? {
        val text = context.preferences.data.first()[selectedKey] ?: return null
        return runCatching {
            val j = JSONObject(text)
            StudyGroup(j.getString("id"), j.getString("name"),
                Institute(j.getString("instituteId"), j.getString("short"), j.getString("instituteName")))
        }.getOrNull()
    }

    suspend fun select(group: StudyGroup) {
        val text = JSONObject().put("id", group.id).put("name", group.name)
            .put("instituteId", group.institute.id).put("short", group.institute.shortName)
            .put("instituteName", group.institute.name).toString()
        context.preferences.edit { it[selectedKey] = text }
        onScheduleChanged()
    }

    suspend fun theme(): String = context.preferences.data.first()[themeKey] ?: "system"
    suspend fun setTheme(value: String) { context.preferences.edit { it[themeKey] = value } }

    private suspend fun <T> source(key: String, path: String, force: Boolean = false, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val old = cache.get(key)
                if (!force && old != null && System.currentTimeMillis() - old.checkedAt < 15 * 60_000) {
                    return@withLock parse(old.html)
                }
                val request = Request.Builder().url("https://www.kstu.ru/$path")
                    .header("User-Agent", "KNITUshka-Android/1.0 (student timetable)").build()
                val html = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty response")
                    if (body.contentLength() > 3_000_000) throw SourceFormatException()
                    body.string().also { if (it.length > 3_000_000) throw SourceFormatException() }
                }
                val parsed = parse(html) // Never replace a good cache with an error page.
                cache.put(CacheEntry(key, html, System.currentTimeMillis()))
                parsed
            }
        }

    suspend fun institutes(): List<Institute> = try {
        source("institutes", "www_GFgrid.jsp", parse = ScheduleParser::institutes)
    } catch (e: IOException) {
        withContext(Dispatchers.IO) {
            cache.get("institutes")?.let { ScheduleParser.institutes(it.html) } ?: throw e
        }
    }

    suspend fun groups(institute: Institute): List<StudyGroup> = try {
        source("groups:${institute.id}", "www_GFgrid.jsp?f=${institute.id}") { ScheduleParser.groups(it, institute) }
    } catch (e: IOException) {
        withContext(Dispatchers.IO) {
            cache.get("groups:${institute.id}")?.let { ScheduleParser.groups(it.html, institute) } ?: throw e
        }
    }

    private fun key(group: StudyGroup, date: LocalDate) = "week:${group.institute.id}:${group.id}:${date.monday()}"

    suspend fun cached(group: StudyGroup, date: LocalDate): CachedWeek? = withContext(Dispatchers.IO) {
        cache.get(key(group, date))?.let {
            runCatching { CachedWeek(ScheduleParser.week(it.html, date), it.checkedAt) }.getOrNull()
        }
    }

    suspend fun refresh(group: StudyGroup, date: LocalDate, force: Boolean = false): CachedWeek {
        source(key(group, date), "www_Ggrid.jsp?f=${group.institute.id}&g=${group.id}&d=${date.monday()}", force) {
            ScheduleParser.week(it, date)
        }
        return (cached(group, date) ?: throw SourceFormatException()).also { onScheduleChanged() }
    }

    suspend fun refreshWindow(group: StudyGroup) {
        for (offset in listOf(0L, 1L, 2L, -1L)) refresh(group, today().plusWeeks(offset))
    }
}
