package com.qq.knitushka.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.qq.knitushka.R

@Suppress("DEPRECATION")
class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return object : RemoteViewsFactory {
            private var snapshot: WidgetSnapshot? = null
            override fun onCreate() { onDataSetChanged() }
            override fun onDataSetChanged() { snapshot = WidgetStorage(applicationContext).snapshot(id) }
            override fun onDestroy() { snapshot = null }
            override fun getCount() = snapshot?.lessons?.size ?: 0
            override fun getViewAt(position: Int): RemoteViews? {
                val row = snapshot?.lessons?.getOrNull(position) ?: return null
                return lessonViews(packageName, row, Intent().putExtra(
                    ScheduleWidgetProvider.EXTRA_DATE, snapshot?.date.toString()))
            }
            override fun getLoadingView(): RemoteViews? = null
            override fun getViewTypeCount() = 1
            override fun getItemId(position: Int) = position.toLong()
            override fun hasStableIds() = false
        }
    }

    companion object {
        internal fun lessonViews(packageName: String, row: WidgetLesson, click: Intent): RemoteViews =
            RemoteViews(packageName, R.layout.schedule_widget_lesson).apply {
                setTextViewText(R.id.widget_lesson_number, row.number.toString())
                setTextViewText(R.id.widget_lesson_subject, row.subject)
                setTextViewText(R.id.widget_lesson_time, row.time)
                setTextViewText(R.id.widget_lesson_room, row.room.ifBlank { "Аудитория не указана" })
                setOnClickFillInIntent(R.id.widget_lesson_root, click)
            }
    }
}
