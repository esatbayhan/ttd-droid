package dev.bayhan.ttd.droid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import dev.bayhan.ttd.droid.R
import dev.bayhan.ttd.droid.config.AppConfig
import dev.bayhan.ttd.droid.config.NotifyConfig
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    autoPrefillView: Boolean = true,
    onAutoPrefillViewChange: (Boolean) -> Unit = {},
    showFullTaskText: Boolean = false,
    onShowFullTaskTextChange: (Boolean) -> Unit = {},
    hideDateValuesInEditor: Boolean = true,
    onHideDateValuesInEditorChange: (Boolean) -> Unit = {},
    hideDateValuesInList: Boolean = true,
    onHideDateValuesInListChange: (Boolean) -> Unit = {},
    notifyDue: NotifyConfig = NotifyConfig(false, 9, 0),
    onNotifyDueChange: (NotifyConfig) -> Unit = {},
    notifyScheduled: NotifyConfig = NotifyConfig(false, 7, 0),
    onNotifyScheduledChange: (NotifyConfig) -> Unit = {},
    notifyStarting: NotifyConfig = NotifyConfig(false, 10, 0),
    onNotifyStartingChange: (NotifyConfig) -> Unit = {},
    showTaskCounts: Boolean = true,
    onShowTaskCountsChange: (Boolean) -> Unit = {},
    hideUpdatedDate: Boolean = true,
    onHideUpdatedDateChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var taskDirUri by remember { mutableStateOf(AppConfig.getTaskDirUri(context)) }
    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            AppConfig.setTaskDirUri(context, uri)
            taskDirUri = uri
        }
    }

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_task_directory)) },
            supportingContent = { Text(taskDirUri?.toString() ?: stringResource(R.string.settings_not_set)) })
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { dirPicker.launch(null) }) { Text(stringResource(R.string.choose_directory)) }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_auto_prefill)) },
            supportingContent = { Text(stringResource(R.string.settings_auto_prefill_desc)) },
            trailingContent = {
                Switch(checked = autoPrefillView, onCheckedChange = onAutoPrefillViewChange)
            }
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_show_full_text)) },
            supportingContent = { Text(stringResource(R.string.settings_show_full_text_desc)) },
            trailingContent = {
                Switch(checked = showFullTaskText, onCheckedChange = onShowFullTaskTextChange)
            }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_hide_dates_editor)) },
            supportingContent = { Text(stringResource(R.string.settings_hide_dates_editor_desc)) },
            trailingContent = {
                Switch(checked = hideDateValuesInEditor, onCheckedChange = onHideDateValuesInEditorChange)
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_hide_dates_list)) },
            supportingContent = { Text(stringResource(R.string.settings_hide_dates_list_desc)) },
            trailingContent = {
                Switch(checked = hideDateValuesInList, onCheckedChange = onHideDateValuesInListChange)
            }
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_show_task_counts)) },
            supportingContent = { Text(stringResource(R.string.settings_show_task_counts_desc)) },
            trailingContent = {
                Switch(checked = showTaskCounts, onCheckedChange = onShowTaskCountsChange)
            }
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_show_updated_date)) },
            supportingContent = { Text(stringResource(R.string.settings_show_updated_date_desc)) },
            trailingContent = {
                Switch(checked = !hideUpdatedDate, onCheckedChange = { onHideUpdatedDateChange(!it) })
            }
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        NotificationRow(
            label = stringResource(R.string.settings_notify_due),
            config = notifyDue,
            onConfigChange = onNotifyDueChange
        )
        NotificationRow(
            label = stringResource(R.string.settings_notify_scheduled),
            config = notifyScheduled,
            onConfigChange = onNotifyScheduledChange
        )
        NotificationRow(
            label = stringResource(R.string.settings_notify_starting),
            config = notifyStarting,
            onConfigChange = onNotifyStartingChange
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        ListItem(headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text(stringResource(R.string.settings_version_info)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationRow(
    label: String,
    config: NotifyConfig,
    onConfigChange: (NotifyConfig) -> Unit
) {
    val timeFormat = remember { DateTimeFormatter.ofPattern("h:mm a") }
    var showTimePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onConfigChange(config.copy(enabled = true))
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = config.hour,
            initialMinute = config.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(label) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    onConfigChange(config.copy(hour = timePickerState.hour, minute = timePickerState.minute))
                }) { Text(stringResource(R.string.picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.picker_cancel)) }
            }
        )
    }

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            if (config.enabled) {
                val time = LocalTime.of(config.hour, config.minute)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_daily_at, time.format(timeFormat)) + " — ")
                    Text(
                        stringResource(R.string.settings_change),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp).clickable { showTimePicker = true }
                    )
                }
            } else {
                Text(stringResource(R.string.settings_disabled))
            }
        },
        trailingContent = {
            Switch(
                checked = config.enabled,
                onCheckedChange = { enabled ->
                    if (enabled && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onConfigChange(config.copy(enabled = enabled))
                    }
                }
            )
        }
    )
}
