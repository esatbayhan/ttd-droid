package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bayhan.ttd.droid.smartlist.*
import dev.bayhan.ttd.droid.smartlist.SmartListSerializer

enum class EditorTab { FORM, CODE }

internal fun initialSmartListFormState(initialList: SmartList?, initialRaw: String?): SmartListFormState {
    val list = initialRaw?.let { SmartListParser.parse(it) } ?: initialList ?: SmartList(name = "")
    val state = SmartListFormState()
    state.icon = list.icon ?: ""
    state.name = list.name
    state.description = list.description ?: ""
    state.conditions.clear()
    list.conditions.forEach { block -> state.conditions.add(FilterBlock(block.conditions)) }
    if (state.conditions.isEmpty()) state.conditions.add(FilterBlock(emptyList()))
    state.sorts.addAll(list.sorts)
    state.groups.addAll(list.groups)
    state.prefills.addAll(list.prefills)
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartListEditorSheet(
    initialList: SmartList? = null,
    initialRaw: String? = null,
    initialFilename: String = "",
    groupOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (filename: String, groupPath: String, raw: String) -> Unit,
    defaultGroup: String = ""
) {
    var selectedTab by remember { mutableStateOf(EditorTab.FORM) }
    var filename by remember { mutableStateOf(initialFilename) }
    var groupPath by remember { mutableStateOf(defaultGroup) }
    var codeRaw by remember {
        mutableStateOf(initialRaw ?: SmartListSerializer.serialize(initialList ?: SmartList(name = "")))
    }
    val formState = remember(initialList, initialRaw) {
        initialSmartListFormState(initialList, initialRaw)
    }
    var showFilenameError by remember { mutableStateOf(false) }

    val codeIsValid = remember(codeRaw) {
        runCatching { SmartListParser.parse(codeRaw) }.isSuccess
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            // Header with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (initialList == null || initialList.name.isEmpty()) "New Smart List" else "Edit Smart List",
                    style = MaterialTheme.typography.titleLarge
                )
                Row {
                    FilterChip(
                        selected = selectedTab == EditorTab.FORM,
                        onClick = { selectedTab = EditorTab.FORM },
                        label = { Text("Form") }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = selectedTab == EditorTab.CODE,
                        onClick = {
                            if (selectedTab == EditorTab.FORM) {
                                codeRaw = SmartListSerializer.serialize(formState.toSmartList())
                            }
                            selectedTab = EditorTab.CODE
                        },
                        label = { Text("Code") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Shared: filename
            OutlinedTextField(
                value = filename,
                onValueChange = { filename = it; showFilenameError = false },
                label = { Text("Filename") },
                suffix = { Text(".list", color = MaterialTheme.colorScheme.outline) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = showFilenameError
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Shared: group dropdown
            var groupExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedTextField(
                    value = groupPath.ifEmpty { "(none)" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Group") },
                    trailingIcon = { Text("▾") },
                    modifier = Modifier.fillMaxWidth().clickable { groupExpanded = true }
                )
                DropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("(none)") },
                        onClick = { groupPath = ""; groupExpanded = false }
                    )
                    groupOptions.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group) },
                            onClick = { groupPath = group; groupExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Editor body
            when (selectedTab) {
                EditorTab.FORM -> {
                    SmartListForm(formState, modifier = Modifier.weight(1f, fill = false))
                }
                EditorTab.CODE -> {
                    SmartListCodeEditor(
                        raw = codeRaw,
                        onRawChange = { codeRaw = it },
                        modifier = Modifier.heightIn(min = 200.dp, max = 400.dp),
                        validationBanner = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        val fn = filename.trim()
                        if (fn.isEmpty()) {
                            showFilenameError = true
                            return@Button
                        }
                        val finalFilename = if (fn.endsWith(".list")) fn else "$fn.list"
                        val raw = when (selectedTab) {
                            EditorTab.FORM -> SmartListSerializer.serialize(formState.toSmartList())
                            EditorTab.CODE -> codeRaw
                        }
                        onSave(finalFilename, groupPath, raw)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
