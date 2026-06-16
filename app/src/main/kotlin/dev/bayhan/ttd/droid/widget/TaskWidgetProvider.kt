package dev.bayhan.ttd.droid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.bayhan.ttd.droid.MainActivity
import dev.bayhan.ttd.droid.R
import dev.bayhan.ttd.droid.config.AppConfig
import dev.bayhan.ttd.droid.store.TaskStore
import dev.bayhan.ttd.droid.task.TaskQuery

class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, TaskWidgetProvider::class.java)
            )
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id)
            }
        }
    }
}

private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.widget_task_list)
    val store = TaskStore(context)
    val uri = AppConfig.getTaskDirUri(context)
    if (uri != null) {
        store.setRoot(uri)
        val tasks = TaskQuery.defaultSort(store.loadTasks())
        val topTasks = tasks.filter { !it.done }.take(5)
        if (topTasks.isEmpty()) {
            views.setTextViewText(R.id.widget_task_1, "No tasks")
        } else {
            topTasks.forEachIndexed { index, task ->
                val viewId = when (index) {
                    0 -> R.id.widget_task_1; 1 -> R.id.widget_task_2
                    2 -> R.id.widget_task_3; 3 -> R.id.widget_task_4
                    4 -> R.id.widget_task_5; else -> return@forEachIndexed
                }
                views.setTextViewText(viewId, task.description)
            }
        }
    } else {
        views.setTextViewText(R.id.widget_task_1, "Configure task directory")
    }
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
