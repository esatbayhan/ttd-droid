package dev.bayhan.ttd.droid.config

import android.content.Context
import android.net.Uri

data class NotifyConfig(val enabled: Boolean, val hour: Int, val minute: Int)

object AppConfig {

    private const val PREFS_NAME = "ttd_prefs"
    private const val KEY_TASK_DIR_URI = "task_dir_uri"
    private const val KEY_AUTO_PREFILL_VIEW = "auto_prefill_view"
    private const val KEY_SHOW_FULL_TASK_TEXT = "show_full_task_text"
    private const val KEY_NOTIFY_DUE_ENABLED = "notify_due_enabled"
    private const val KEY_NOTIFY_DUE_HOUR = "notify_due_hour"
    private const val KEY_NOTIFY_DUE_MINUTE = "notify_due_minute"
    private const val KEY_NOTIFY_SCHEDULED_ENABLED = "notify_scheduled_enabled"
    private const val KEY_NOTIFY_SCHEDULED_HOUR = "notify_scheduled_hour"
    private const val KEY_NOTIFY_SCHEDULED_MINUTE = "notify_scheduled_minute"
    private const val KEY_NOTIFY_STARTING_ENABLED = "notify_starting_enabled"
    private const val KEY_NOTIFY_STARTING_HOUR = "notify_starting_hour"
    private const val KEY_NOTIFY_STARTING_MINUTE = "notify_starting_minute"
    private const val KEY_NOTIFIED_TASKS = "notified_tasks"
    private const val KEY_HIDE_DATE_VALUES_IN_EDITOR = "hide_date_values_in_editor"
    private const val KEY_HIDE_DATE_VALUES_IN_LIST = "hide_date_values_in_list"
    private const val KEY_SHOW_TASK_COUNTS = "show_task_counts"
    private const val KEY_HIDE_UPDATED_DATE = "hide_updated_date"

    fun getTaskDirUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_TASK_DIR_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    fun setTaskDirUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TASK_DIR_URI, uri.toString()).apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getTaskDirUri(context) != null
    }

    fun getAutoPrefillView(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_PREFILL_VIEW, true)
    }

    fun setAutoPrefillView(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_PREFILL_VIEW, enabled).apply()
    }

    fun getShowFullTaskText(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_FULL_TASK_TEXT, false)
    }

    fun setShowFullTaskText(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_FULL_TASK_TEXT, enabled).apply()
    }

    fun getHideDateValuesInEditor(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_DATE_VALUES_IN_EDITOR, true)
    }

    fun setHideDateValuesInEditor(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_DATE_VALUES_IN_EDITOR, enabled).apply()
    }

    fun getHideDateValuesInList(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_DATE_VALUES_IN_LIST, true)
    }

    fun setHideDateValuesInList(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_DATE_VALUES_IN_LIST, enabled).apply()
    }

    fun getShowTaskCounts(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_TASK_COUNTS, true)
    }

    fun setShowTaskCounts(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_TASK_COUNTS, enabled).apply()
    }

    fun getNotifyDue(context: Context): NotifyConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NotifyConfig(
            enabled = prefs.getBoolean(KEY_NOTIFY_DUE_ENABLED, false),
            hour = prefs.getInt(KEY_NOTIFY_DUE_HOUR, 9),
            minute = prefs.getInt(KEY_NOTIFY_DUE_MINUTE, 0)
        )
    }

    fun setNotifyDue(context: Context, config: NotifyConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIFY_DUE_ENABLED, config.enabled)
            .putInt(KEY_NOTIFY_DUE_HOUR, config.hour)
            .putInt(KEY_NOTIFY_DUE_MINUTE, config.minute)
            .apply()
    }

    fun getNotifyScheduled(context: Context): NotifyConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NotifyConfig(
            enabled = prefs.getBoolean(KEY_NOTIFY_SCHEDULED_ENABLED, false),
            hour = prefs.getInt(KEY_NOTIFY_SCHEDULED_HOUR, 7),
            minute = prefs.getInt(KEY_NOTIFY_SCHEDULED_MINUTE, 0)
        )
    }

    fun setNotifyScheduled(context: Context, config: NotifyConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIFY_SCHEDULED_ENABLED, config.enabled)
            .putInt(KEY_NOTIFY_SCHEDULED_HOUR, config.hour)
            .putInt(KEY_NOTIFY_SCHEDULED_MINUTE, config.minute)
            .apply()
    }

    fun getNotifyStarting(context: Context): NotifyConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NotifyConfig(
            enabled = prefs.getBoolean(KEY_NOTIFY_STARTING_ENABLED, false),
            hour = prefs.getInt(KEY_NOTIFY_STARTING_HOUR, 10),
            minute = prefs.getInt(KEY_NOTIFY_STARTING_MINUTE, 0)
        )
    }

    fun setNotifyStarting(context: Context, config: NotifyConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIFY_STARTING_ENABLED, config.enabled)
            .putInt(KEY_NOTIFY_STARTING_HOUR, config.hour)
            .putInt(KEY_NOTIFY_STARTING_MINUTE, config.minute)
            .apply()
    }

    fun getNotifiedTasks(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_NOTIFIED_TASKS, emptySet())?.toSet() ?: emptySet()
    }

    fun markTaskNotified(context: Context, filename: String, key: String, date: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_NOTIFIED_TASKS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add("$filename|$key|$date")
        prefs.edit().putStringSet(KEY_NOTIFIED_TASKS, current).apply()
    }

    fun cleanNotifiedTasks(context: Context, validEntries: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_NOTIFIED_TASKS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.retainAll(validEntries)) {
            prefs.edit().putStringSet(KEY_NOTIFIED_TASKS, current).apply()
        }
    }

    fun getHideUpdatedDate(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_UPDATED_DATE, true)
    }

    fun setHideUpdatedDate(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_UPDATED_DATE, enabled).apply()
    }
}
