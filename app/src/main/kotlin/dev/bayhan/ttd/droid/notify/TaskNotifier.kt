package dev.bayhan.ttd.droid.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import dev.bayhan.ttd.droid.MainActivity
import dev.bayhan.ttd.droid.config.AppConfig
import dev.bayhan.ttd.droid.store.TaskStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object TaskNotifier {

    const val CHANNEL_ID = "task_reminders"
    private const val WORK_NAME = "notification_schedule"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Task Reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Reminders for due and scheduled tasks" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotificationScheduleWork>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun showNotification(context: Context, title: String, description: String, filename: String, key: String) {
        val truncated = if (description.length > 100) description.take(100) + "..." else description
        val notificationId = "$filename|$key".hashCode()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("notification_task_filename", filename)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(truncated)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    fun scheduleAlarms(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val store = TaskStore(context)
        val uri = AppConfig.getTaskDirUri(context) ?: return
        store.setRoot(uri)
        val tasks = store.loadTasks()
        val today = LocalDate.now()

        val keyConfigs = listOf(
            Triple("due", AppConfig.getNotifyDue(context), "Task Due"),
            Triple("scheduled", AppConfig.getNotifyScheduled(context), "Task Scheduled"),
            Triple("starting", AppConfig.getNotifyStarting(context), "Task Starting")
        )

        val validNotifiedEntries = mutableSetOf<String>()

        for ((key, config, title) in keyConfigs) {
            if (!config.enabled) {
                for (task in tasks) {
                    val requestCode = "$task.filename|$key".hashCode()
                    val dummyIntent = Intent(context, NotificationReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        context, requestCode, dummyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(pending)
                }
                continue
            }

            val notifyTime = LocalTime.of(config.hour, config.minute)

            for (task in tasks) {
                if (task.done) continue

                val dateStr = task.tags[key] ?: continue
                val date = try { LocalDate.parse(dateStr) } catch (_: Exception) { continue }

                val entry = "$task.filename|$key|$date"

                if (date > today) {
                    validNotifiedEntries.add(entry)
                    continue
                }

                if (AppConfig.getNotifiedTasks(context).contains(entry)) {
                    validNotifiedEntries.add(entry)
                    continue
                }

                val dateTime = date.atTime(notifyTime)
                val triggerMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                if (date == today && triggerMillis > System.currentTimeMillis() && alarmManager.canScheduleExactAlarms()) {
                    val alarmIntent = Intent(context, NotificationReceiver::class.java).apply {
                        putExtra("task_description", task.description)
                        putExtra("notification_key", key)
                        putExtra("notification_title", title)
                        putExtra("task_filename", task.filename)
                    }
                    val requestCode = "$task.filename|$key".hashCode()
                    val pending = PendingIntent.getBroadcast(
                        context, requestCode, alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(pending)
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
                } else {
                    showNotification(context, title, task.description, task.filename, key)
                }

                AppConfig.markTaskNotified(context, task.filename, key, date.toString())
                validNotifiedEntries.add(entry)
            }
        }

        AppConfig.cleanNotifiedTasks(context, validNotifiedEntries)
    }
}

class NotificationScheduleWork(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        TaskNotifier.scheduleAlarms(applicationContext)
        return Result.success()
    }
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val description = intent.getStringExtra("task_description") ?: return
        val key = intent.getStringExtra("notification_key") ?: "due"
        val title = intent.getStringExtra("notification_title") ?: "Task Due"
        val filename = intent.getStringExtra("task_filename") ?: return
        TaskNotifier.showNotification(context, title, description, filename, key)
    }
}
