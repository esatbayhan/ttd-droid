package dev.bayhan.ttd.droid.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bayhan.ttd.droid.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import dev.bayhan.ttd.droid.smartlist.LoadedSmartList
import dev.bayhan.ttd.droid.smartlist.SmartListEval
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.task.TaskDateTime
import dev.bayhan.ttd.droid.task.TaskParser

sealed class EditorMode {
    data class Add(val prefill: String? = null) : EditorMode()
    data class Edit(val filename: String, val raw: String) : EditorMode()
}

private val dateKeys = listOf("due", "scheduled", "starting")

private fun dateTokenRegex(key: String) =
    Regex("(?<!\\S)${Regex.escape(key)}:(\\S+)(?=\\s|$)")

private fun firstDateToken(key: String, text: String) = dateTokenRegex(key).find(text)

internal fun getDateValue(key: String, text: String): String? {
    val value = firstDateToken(key, text)?.groupValues?.get(1) ?: return null
    return value.takeIf { TaskDateTime.parse(it) != null }
}

internal fun setDateValue(key: String, date: String, text: String): String {
    val match = firstDateToken(key, text) ?: return "$text ${key}:$date"
    return if (TaskDateTime.parse(match.groupValues[1]) != null) {
        text.replaceRange(match.range, "${key}:$date")
    } else {
        text.substring(0, match.range.first) + "${key}:$date " + text.substring(match.range.first)
    }
}

internal fun clearDateValue(key: String, text: String): String {
    val match = firstDateToken(key, text) ?: return text
    if (TaskDateTime.parse(match.groupValues[1]) == null) return text
    val start = if (match.range.first > 0 && text[match.range.first - 1].isWhitespace()) {
        match.range.first - 1
    } else {
        match.range.first
    }
    val end = if (start == match.range.first && match.range.last + 1 < text.length) {
        match.range.last + 2
    } else {
        match.range.last + 1
    }
    return text.removeRange(start, end)
}

internal fun stripDateTokens(raw: String): String {
    var result = raw
    for (key in dateKeys) {
        result = clearDateValue(key, result)
    }
    return if (result == raw) raw else result.trim()
}

internal fun stripUpdatedToken(raw: String): String {
    val result = clearDateValue("updated", raw)
    return if (result == raw) raw else result.trim()
}

internal fun replaceUpdatedDate(raw: String, date: String): String = setDateValue("updated", date, raw)

internal fun setTimeOnDate(value: String, time: String): String =
    "${value.substringBefore('T')}T$time"

internal fun clearTimeFromDate(value: String): String = value.substringBefore('T')

internal fun formatDateTokensForDisplay(raw: String): String {
    var result = raw
    for (key in dateKeys + "updated") {
        val match = firstDateToken(key, result) ?: continue
        val value = match.groupValues[1]
        if (TaskDateTime.parse(value) != null) {
            result = result.replaceRange(match.range, "$key:${TaskDateTime.formatForDisplay(value)}")
        }
    }
    return result
}

internal fun reconstructTaskText(description: String, dates: Map<String, String?>): String {
    var result = description.trim()
    for ((key, value) in dates) {
        if (value == null) continue
        val match = firstDateToken(key, result)
        result = when {
            match != null -> result.substring(0, match.range.first) + "$key:$value " + result.substring(match.range.first)
            result.isEmpty() -> "$key:$value"
            else -> "$result $key:$value"
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(
    editorMode: EditorMode,
    allProjects: List<String>,
    allContexts: List<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    hideDateValues: Boolean = false,
    hideUpdatedDate: Boolean = true,
    smartLists: List<LoadedSmartList> = emptyList(),
    onNavigateToItem: ((DrawerItem) -> Unit)? = null
) {
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val initialRaw = remember(editorMode) {
        when (editorMode) {
            is EditorMode.Add -> editorMode.prefill ?: ""
            is EditorMode.Edit -> editorMode.raw
        }
    }
    val editorDates = remember(editorMode) {
        mutableStateMapOf(
            "due" to getDateValue("due", initialRaw),
            "scheduled" to getDateValue("scheduled", initialRaw),
            "starting" to getDateValue("starting", initialRaw)
        )
    }
    var text by remember(editorMode) {
        var stripped = if (hideDateValues) stripDateTokens(initialRaw) else initialRaw
        if (hideUpdatedDate) stripped = stripUpdatedToken(stripped)
        mutableStateOf(stripped)
    }
    var activeDateKey by remember { mutableStateOf("due") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val title = when (editorMode) {
        is EditorMode.Add -> stringResource(R.string.editor_title_add)
        is EditorMode.Edit -> stringResource(R.string.editor_title_edit)
    }

    val dateValues = if (hideDateValues) {
        editorDates.toMap()
    } else {
        remember(text) {
            dateKeys.associateWith { getDateValue(it, text) }
        }
    }

    val todayUtcMillis = today.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    var skipInitial by remember { mutableStateOf(true) }
    LaunchedEffect(hideDateValues) {
        if (skipInitial) {
            skipInitial = false
            return@LaunchedEffect
        }
        if (hideDateValues) {
            val raw = text
            val currentDates = mutableMapOf<String, String?>()
            for (key in dateKeys) {
                currentDates[key] = getDateValue(key, raw)
                currentDates[key]?.let { editorDates[key] = it }
            }
            text = stripDateTokens(raw)
        } else {
            val rebuilt = reconstructTaskText(text, editorDates.toMap())
            text = rebuilt
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        var finalText = if (hideDateValues) reconstructTaskText(text, editorDates.toMap()).trim()
                                       else text.trim()
                        if (hideUpdatedDate) finalText = stripUpdatedToken(finalText)
                        onSave(finalText)
                    },
                    enabled = text.isNotBlank()
                ) { Text(stringResource(R.string.editor_save)) }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.editor_description_hint)) },
                    minLines = 3,
                    maxLines = Int.MAX_VALUE
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    dateKeys.forEach { key ->
                        val isActive = key == activeDateKey
                        val value = dateValues[key]
                        val label = if (value != null) {
                            "$key: ${TaskDateTime.formatForDisplay(value)}"
                        } else {
                            key
                        }
                        FilterChip(
                            selected = isActive,
                            onClick = {
                                if (isActive && value != null) {
                                    if (hideDateValues) {
                                        editorDates[key] = null
                                    } else {
                                        text = clearDateValue(key, text)
                                    }
                                }
                                activeDateKey = key
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    AssistChip(
                        onClick = {
                            val date = today.format(fmt)
                            if (hideDateValues) editorDates[activeDateKey] = date
                            else text = setDateValue(activeDateKey, date, text)
                        },
                        label = { Text(stringResource(R.string.editor_today)) }
                    )
                    AssistChip(
                        onClick = {
                            val date = today.plusDays(1).format(fmt)
                            if (hideDateValues) editorDates[activeDateKey] = date
                            else text = setDateValue(activeDateKey, date, text)
                        },
                        label = { Text(stringResource(R.string.editor_tomorrow)) }
                    )
                    AssistChip(
                        onClick = {
                            val date = today.plusDays(3).format(fmt)
                            if (hideDateValues) editorDates[activeDateKey] = date
                            else text = setDateValue(activeDateKey, date, text)
                        },
                        label = { Text(stringResource(R.string.editor_plus_3d)) }
                    )
                    AssistChip(
                        onClick = { showDatePicker = true },
                        label = { Text(stringResource(R.string.editor_pick_date)) },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.editor_pick_date_cd), modifier = Modifier.size(16.dp)) }
                    )
                    dateValues[activeDateKey]?.let { currentValue ->
                        AssistChip(
                            onClick = { showTimePicker = true },
                            label = { Text(stringResource(R.string.editor_pick_time)) }
                        )
                        if ('T' in currentValue) {
                            AssistChip(
                                onClick = {
                                    val dateOnly = clearTimeFromDate(currentValue)
                                    if (hideDateValues) editorDates[activeDateKey] = dateOnly
                                    else text = setDateValue(activeDateKey, dateOnly, text)
                                },
                                label = { Text(stringResource(R.string.editor_clear_time)) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (allProjects.isNotEmpty()) {
                    Text(stringResource(R.string.editor_section_projects), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        allProjects.forEach { project ->
                            val tag = "+$project"
                            AssistChip(
                                onClick = {
                                    if (!text.contains(" $tag") && !text.startsWith(tag)) {
                                        text = "$text $tag"
                                    }
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }

                if (allContexts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.editor_section_contexts), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        allContexts.forEach { ctx ->
                            val tag = "@$ctx"
                            AssistChip(
                                onClick = {
                                    if (!text.contains(" $tag") && !text.startsWith(tag)) {
                                        text = "$text $tag"
                                    }
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }

                val parsedTask = remember(editorMode) {
                    when (editorMode) {
                        is EditorMode.Edit -> TaskParser.parse(editorMode.raw)
                        else -> null
                    }
                }

                if (editorMode is EditorMode.Edit && (parsedTask?.creationDate != null || (!hideUpdatedDate && parsedTask?.tags?.containsKey("updated") == true))) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.editor_section_additional), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    parsedTask?.creationDate?.let {
                        Text(stringResource(R.string.editor_created_date, TaskDateTime.formatForDisplay(it)), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!hideUpdatedDate) {
                        parsedTask?.tags?.get("updated")?.let {
                            Text(stringResource(R.string.editor_updated_date, TaskDateTime.formatForDisplay(it)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (editorMode is EditorMode.Edit && parsedTask != null && onNavigateToItem != null) {
                    val matchingSmartLists = remember(smartLists, parsedTask) {
                        smartLists.filter { it.list.conditions.isNotEmpty() && SmartListEval.matches(parsedTask, it.list) }
                            .sortedBy { it.fileName }
                    }
                    val hasBacklinks = matchingSmartLists.isNotEmpty() || parsedTask.projects.isNotEmpty() || parsedTask.contexts.isNotEmpty()

                    if (hasBacklinks) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.editor_in_lists), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))

                        matchingSmartLists.forEach { loaded ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigateToItem(DrawerItem.SmartList(loaded.list.name, loaded.group, loaded.fileName, loaded.list))
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                loaded.list.icon?.let { Text("$it  ") }
                                Text(loaded.list.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.editor_open_list_cd, loaded.list.name), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        parsedTask.projects.forEach { project ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigateToItem(DrawerItem.Project(project))
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text("+$project", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.editor_open_list_cd, project), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        parsedTask.contexts.forEach { ctx ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigateToItem(DrawerItem.Context(ctx))
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text("@$ctx", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.editor_open_list_cd, ctx), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = todayUtcMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= todayUtcMillis
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val date = selected.format(fmt)
                        if (hideDateValues) editorDates[activeDateKey] = date
                        else text = setDateValue(activeDateKey, date, text)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.picker_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val current = dateValues[activeDateKey]?.let(TaskDateTime::parse)
        val timePickerState = rememberTimePickerState(
            initialHour = current?.time?.hour ?: 9,
            initialMinute = current?.time?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val existing = dateValues[activeDateKey]
                    if (existing != null) {
                        val time = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                        val value = setTimeOnDate(existing, time)
                        if (hideDateValues) editorDates[activeDateKey] = value
                        else text = setDateValue(activeDateKey, value, text)
                    }
                    showTimePicker = false
                }) { Text(stringResource(R.string.picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.picker_cancel))
                }
            }
        )
    }
}
