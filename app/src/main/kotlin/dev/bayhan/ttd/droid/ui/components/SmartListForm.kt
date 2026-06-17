package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bayhan.ttd.droid.smartlist.*

class SmartListFormState(
    var icon: String = "",
    var name: String = "",
    var description: String = "",
    val conditions: SnapshotStateList<FilterBlock> = mutableStateListOf(FilterBlock(emptyList())),
    val sorts: SnapshotStateList<Directive> = mutableStateListOf(),
    val groups: SnapshotStateList<Directive> = mutableStateListOf(),
    val prefills: SnapshotStateList<Prefill> = mutableStateListOf()
)

fun SmartListFormState.toSmartList(): SmartList = SmartList(
    name = name.ifBlank { "Untitled" },
    icon = icon.ifBlank { null },
    description = description.ifBlank { null },
    conditions = conditions.filter { it.conditions.isNotEmpty() },
    sorts = sorts.toList(),
    groups = groups.toList(),
    prefills = prefills.toList()
)

@Composable
fun SmartListForm(
    state: SmartListFormState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        // Icon + Name row
        Row(verticalAlignment = Alignment.CenterVertically) {
            var showIconPicker by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .clickable { showIconPicker = true },
                contentAlignment = Alignment.Center
            ) {
                Text(state.icon.ifBlank { "\uD83D\uDCCB" }, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = { state.name = it },
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.description,
            onValueChange = { state.description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )

        // --- FILTERS ---
        SectionHeader("FILTERS", MaterialTheme.colorScheme.primary) {
            state.conditions.add(FilterBlock(emptyList()))
        }

        for ((index, block) in state.conditions.withIndex()) {
            FilterBlockCard(
                block = block,
                blockIndex = index,
                onAddCondition = { condition ->
                    state.conditions[index] = FilterBlock(block.conditions + condition)
                },
                onRemoveCondition = { idx ->
                    state.conditions[index] = FilterBlock(block.conditions.toMutableList().apply { removeAt(idx) })
                }
            )
            if (index < state.conditions.size - 1) {
                Text("OR", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        // --- DIRECTIVES ---
        SectionHeader("DIRECTIVES", MaterialTheme.colorScheme.tertiary)
        for ((index, directive) in state.sorts.withIndex()) {
            ChipWithRemove(
                label = "sort by ${directive.field} ${if (directive.ascending) "asc" else "desc"}",
                onRemove = { state.sorts.removeAt(index) }
            )
        }
        for ((index, directive) in state.groups.withIndex()) {
            ChipWithRemove(
                label = "group by ${directive.field} ${if (directive.ascending) "asc" else "desc"}",
                onRemove = { state.groups.removeAt(index) }
            )
        }
        DirectiveBuilder(onAdd = { type, field, asc ->
            val dir = Directive(field, asc)
            if (type == "sort") state.sorts.add(dir) else state.groups.add(dir)
        })

        // --- PREFILLS ---
        SectionHeader("PREFILLS", MaterialTheme.colorScheme.secondary)
        for ((index, prefill) in state.prefills.withIndex()) {
            ChipWithRemove(
                label = "${prefill.field}: ${prefill.value}",
                onRemove = { state.prefills.removeAt(index) }
            )
        }
        PrefillBuilder(onAdd = { field, value ->
            state.prefills.add(Prefill(field, value))
        })

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(title: String, color: androidx.compose.ui.graphics.Color, onAdd: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        if (onAdd != null) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                Text("+ OR", fontSize = 10.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FilterBlockCard(
    block: FilterBlock,
    blockIndex: Int,
    onAddCondition: (Condition) -> Unit,
    onRemoveCondition: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Block ${blockIndex + 1}${if (blockIndex > 0) " (OR)" else ""} — all of these must match",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(4.dp))
            for ((i, cond) in block.conditions.withIndex()) {
                ChipWithRemove(
                    label = conditionLabel(cond),
                    onRemove = { onRemoveCondition(i) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ConditionBuilder(onAdd = onAddCondition)
        }
    }
}

@Composable
private fun ConditionBuilder(onAdd: (Condition) -> Unit) {
    val fields = listOf(
        "due" to "due", "scheduled" to "scheduled", "starting" to "starting",
        "updated" to "updated", "creation_date" to "creation_date", "priority" to "priority",
        "project" to "project", "context" to "context", "description" to "description",
        "done" to "done"
    )

    var showBuilder by remember { mutableStateOf(false) }
    var selectedField by remember { mutableStateOf("due") }
    var selectedOp by remember { mutableStateOf<String?>(null) }
    var textValue by remember { mutableStateOf("") }
    var priorityValue by remember { mutableStateOf('A') }
    var datePreset by remember { mutableStateOf("today") }

    val operators = availableOperators(selectedField)
    val showValue = operatorNeedsValue(selectedField, selectedOp)

    if (!showBuilder) {
        TextButton(onClick = { showBuilder = true; selectedOp = operators.firstOrNull() }) {
            Text("+ Add condition")
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            var fieldExpanded by remember { mutableStateOf(false) }
            Box {
                SuggestionChip(
                    onClick = { fieldExpanded = true },
                    label = { Text(selectedField, fontSize = 11.sp) }
                )
                DropdownMenu(expanded = fieldExpanded, onDismissRequest = { fieldExpanded = false }) {
                    fields.forEach { (label, _) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedField = label
                                selectedOp = availableOperators(label).firstOrNull()
                                fieldExpanded = false
                            }
                        )
                    }
                }
            }

            Text("\u2192", modifier = Modifier.padding(horizontal = 2.dp), fontSize = 12.sp)

            if (operators.isNotEmpty()) {
                var opExpanded by remember { mutableStateOf(false) }
                Box {
                    SuggestionChip(
                        onClick = { opExpanded = true },
                        label = { Text(selectedOp ?: "", fontSize = 11.sp) }
                    )
                    DropdownMenu(expanded = opExpanded, onDismissRequest = { opExpanded = false }) {
                        operators.forEach { op ->
                            DropdownMenuItem(
                                text = { Text(op) },
                                onClick = { selectedOp = op; opExpanded = false }
                            )
                        }
                    }
                }
            }

            if (showValue) {
                Text("\u2192", modifier = Modifier.padding(horizontal = 2.dp), fontSize = 12.sp)

                when {
                    isDateField(selectedField) -> {
                        LazyRow(Modifier.weight(1f)) {
                            items(listOf("today", "today+1", "today+3", "today+7", "today+14", "today+30")) { preset ->
                                SuggestionChip(
                                    onClick = { datePreset = preset },
                                    label = { Text(preset, fontSize = 10.sp) },
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                            }
                        }
                    }
                    selectedField == "priority" -> {
                        var priorityExpanded by remember { mutableStateOf(false) }
                        Box {
                            SuggestionChip(
                                onClick = { priorityExpanded = true },
                                label = { Text(priorityValue.toString(), fontSize = 11.sp) }
                            )
                            DropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
                                ('A'..'Z').forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.toString()) },
                                        onClick = { priorityValue = c; priorityExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                    selectedField in listOf("project", "context", "description") -> {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            modifier = Modifier.weight(1f).height(36.dp),
                            textStyle = TextStyle(fontSize = 11.sp),
                            singleLine = true,
                            placeholder = { Text("value", fontSize = 10.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = {
                val cond = buildCondition(selectedField, selectedOp, textValue, priorityValue, datePreset)
                if (cond != null) {
                    onAdd(cond)
                    showBuilder = false
                    selectedOp = null
                    textValue = ""
                    priorityValue = 'A'
                    datePreset = "today"
                }
            }, modifier = Modifier.size(24.dp)) {
                Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }
            IconButton(onClick = { showBuilder = false }, modifier = Modifier.size(24.dp)) {
                Text("\u2717", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DirectiveBuilder(onAdd: (type: String, field: String, ascending: Boolean) -> Unit) {
    var showBuilder by remember { mutableStateOf(false) }
    val types = listOf("sort by", "group by")
    val fields = listOf("due", "scheduled", "starting", "updated", "creation_date",
        "priority", "project", "context", "description", "done")
    val dirs = listOf("asc", "desc")

    if (!showBuilder) {
        TextButton(onClick = { showBuilder = true }) { Text("+ Add directive") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            var typeExpanded by remember { mutableStateOf(false) }
            var type by remember { mutableStateOf("sort by") }
            Box {
                SuggestionChip(onClick = { typeExpanded = true },
                    label = { Text(type, fontSize = 11.sp) })
                DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    types.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { type = it; typeExpanded = false })
                    }
                }
            }

            var fieldExpanded by remember { mutableStateOf(false) }
            var field by remember { mutableStateOf("priority") }
            Box {
                SuggestionChip(onClick = { fieldExpanded = true },
                    label = { Text(field, fontSize = 11.sp) })
                DropdownMenu(expanded = fieldExpanded, onDismissRequest = { fieldExpanded = false }) {
                    fields.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { field = it; fieldExpanded = false })
                    }
                }
            }

            var dirExpanded by remember { mutableStateOf(false) }
            var dir by remember { mutableStateOf("desc") }
            Box {
                SuggestionChip(onClick = { dirExpanded = true },
                    label = { Text(dir, fontSize = 11.sp) })
                DropdownMenu(expanded = dirExpanded, onDismissRequest = { dirExpanded = false }) {
                    dirs.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { dir = it; dirExpanded = false })
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = {
                onAdd(type.removeSuffix(" by"), field, dir == "asc")
                showBuilder = false
            }, modifier = Modifier.size(24.dp)) {
                Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }
            IconButton(onClick = { showBuilder = false }, modifier = Modifier.size(24.dp)) {
                Text("\u2717", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PrefillBuilder(onAdd: (field: String, value: String) -> Unit) {
    var showBuilder by remember { mutableStateOf(false) }
    val fields = listOf("project", "context", "priority", "due", "scheduled", "starting")

    if (!showBuilder) {
        TextButton(onClick = { showBuilder = true }) { Text("+ Add prefill") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            var fieldExpanded by remember { mutableStateOf(false) }
            var field by remember { mutableStateOf("project") }
            Box {
                SuggestionChip(onClick = { fieldExpanded = true },
                    label = { Text(field, fontSize = 11.sp) })
                DropdownMenu(expanded = fieldExpanded, onDismissRequest = { fieldExpanded = false }) {
                    fields.forEach {
                        DropdownMenuItem(text = { Text(it) }, onClick = { field = it; fieldExpanded = false })
                    }
                }
            }

            if (field == "priority") {
                var priorityValue by remember { mutableStateOf('A') }
                var priorityExpanded by remember { mutableStateOf(false) }
                Box {
                    SuggestionChip(onClick = { priorityExpanded = true },
                        label = { Text(priorityValue.toString(), fontSize = 11.sp) })
                    DropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
                        ('A'..'Z').forEach { c ->
                            DropdownMenuItem(text = { Text(c.toString()) },
                                onClick = { priorityValue = c; priorityExpanded = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { onAdd(field, priorityValue.toString()); showBuilder = false },
                    modifier = Modifier.size(24.dp)) {
                    Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            } else if (field in listOf("due", "scheduled", "starting")) {
                var dateValue by remember { mutableStateOf("today") }
                LazyRow(Modifier.weight(1f)) {
                    items(listOf("today", "today+1", "today+3", "today+7")) { preset ->
                        SuggestionChip(
                            onClick = { dateValue = preset },
                            label = { Text(preset, fontSize = 10.sp) },
                            modifier = Modifier.padding(end = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { onAdd(field, dateValue); showBuilder = false },
                    modifier = Modifier.size(24.dp)) {
                    Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            } else {
                var textValue by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.weight(1f).height(36.dp),
                    textStyle = TextStyle(fontSize = 11.sp),
                    singleLine = true,
                    placeholder = { Text("value", fontSize = 10.sp) }
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { onAdd(field, textValue); showBuilder = false },
                    modifier = Modifier.size(24.dp)) {
                    Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }

            IconButton(onClick = { showBuilder = false }, modifier = Modifier.size(24.dp)) {
                Text("\u2717", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ChipWithRemove(label: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    fontSize = 11.sp)
                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                    Text("\u00D7", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// --- Helper functions ---

private fun conditionLabel(cond: Condition): String = when (cond) {
    is DoneCondition -> if (cond.done) "done" else "not done"
    is PriorityCondition -> "priority ${when(cond.op){PriorityOp.ABOVE->"above";PriorityOp.BELOW->"below";PriorityOp.EQ->"="}} ${cond.value}"
    is TextCondition -> "${cond.field.name.lowercase()} ${cond.op.name.lowercase()} ${cond.value}"
    is ExistsCondition -> "${if (cond.has) "has" else "no"} ${cond.field.name.lowercase()}"
    is DateCondition -> "${cond.field.name.lowercase()} ${cond.op.name} ${dateValueString(cond.value)}"
}

private fun dateValueString(dv: DateValue): String = when (dv.anchor) {
    DateAnchor.TODAY -> if (dv.offset == 0) "today" else "today${if (dv.offset > 0) "+" else ""}${dv.offset}"
    DateAnchor.ABSOLUTE -> if (dv.offset == 0) (dv.anchorDate ?: "") else "${dv.anchorDate}${if (dv.offset > 0) "+" else ""}${dv.offset}"
}

private fun availableOperators(field: String): List<String> = when (field) {
    "due", "scheduled", "starting", "updated", "creation_date" ->
        listOf("=", "<", "<=", ">", ">=", "has", "no")
    "priority" -> listOf("=", "above", "below", "has", "no")
    "project", "context", "description" -> listOf("includes", "excludes", "has", "no")
    "done" -> emptyList()
    else -> emptyList()
}

private fun operatorNeedsValue(field: String, op: String?): Boolean = when (field) {
    "done" -> false
    else -> op != null && op !in listOf("has", "no")
}

private fun isDateField(field: String): Boolean =
    field in listOf("due", "scheduled", "starting", "updated", "creation_date")

private fun buildCondition(
    field: String, op: String?, textValue: String,
    priorityValue: Char, datePreset: String
): Condition? {
    if (field == "done") return DoneCondition(done = true)

    return when {
        isDateField(field) -> {
            if (op in listOf("has", "no")) {
                val fieldEnum = parseFieldEnum(field) ?: return null
                ExistsCondition(op == "has", fieldEnum)
            } else {
                val dateField = when (field) {
                    "due" -> DateField.DUE; "scheduled" -> DateField.SCHEDULED
                    "starting" -> DateField.STARTING; "updated" -> DateField.UPDATED
                    "creation_date" -> DateField.CREATION_DATE; else -> return null
                }
                val compOp = when (op) {
                    "=" -> CompareOp.EQ; "<" -> CompareOp.LT
                    "<=" -> CompareOp.LTE; ">" -> CompareOp.GT
                    ">=" -> CompareOp.GTE; else -> return null
                }
                val dv = parseDatePreset(datePreset)
                DateCondition(dateField, compOp, dv)
            }
        }
        field == "priority" -> {
            if (op in listOf("has", "no")) {
                ExistsCondition(op == "has", Field.PRIORITY)
            } else {
                val pOp = when (op) {
                    "=" -> PriorityOp.EQ; "above" -> PriorityOp.ABOVE
                    "below" -> PriorityOp.BELOW; else -> return null
                }
                PriorityCondition(pOp, priorityValue)
            }
        }
        field in listOf("project", "context", "description") -> {
            if (op in listOf("has", "no")) {
                val fieldEnum = parseFieldEnum(field) ?: return null
                ExistsCondition(op == "has", fieldEnum)
            } else {
                val tField = when (field) {
                    "project" -> TextField.PROJECT; "context" -> TextField.CONTEXT
                    "description" -> TextField.DESCRIPTION; else -> return null
                }
                val tOp = when (op) {
                    "includes" -> TextOp.INCLUDES; "excludes" -> TextOp.EXCLUDES
                    else -> return null
                }
                TextCondition(tField, tOp, textValue)
            }
        }
        else -> null
    }
}

private fun parseFieldEnum(field: String): Field? = when (field) {
    "due" -> Field.DUE; "scheduled" -> Field.SCHEDULED
    "starting" -> Field.STARTING; "updated" -> Field.UPDATED
    "creation_date" -> Field.CREATION_DATE; "priority" -> Field.PRIORITY
    "project" -> Field.PROJECT; "context" -> Field.CONTEXT
    "description" -> Field.DESCRIPTION; else -> null
}

private fun parseDatePreset(preset: String): DateValue {
    val todayOffsetRegex = Regex("^today\\s*([+-])\\s*(\\d+)$")
    val match = todayOffsetRegex.find(preset.trim())
    return if (match != null) {
        val sign = if (match.groupValues[1] == "+") 1 else -1
        val num = match.groupValues[2].toIntOrNull() ?: 0
        DateValue(DateAnchor.TODAY, sign * num)
    } else if (preset == "today") {
        DateValue(DateAnchor.TODAY, 0)
    } else {
        DateValue(DateAnchor.TODAY, 0)
    }
}
