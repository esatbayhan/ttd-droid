package dev.bayhan.ttd.droid.widget

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WidgetUpdateWorker {
    private const val WORK_NAME = "widget_update"

    fun enqueue(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWork>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}

class WidgetUpdateWork(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        TaskWidgetProvider.updateAllWidgets(applicationContext)
        return Result.success()
    }
}
