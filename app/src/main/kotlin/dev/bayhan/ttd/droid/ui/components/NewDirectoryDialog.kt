package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NewDirectoryDialog(
    parentOptions: List<String>,
    defaultParent: String = "",
    onDismiss: () -> Unit,
    onCreate: (name: String, parentPath: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var parentPath by remember { mutableStateOf(defaultParent) }
    var parentExpanded by remember { mutableStateOf(false) }
    var showNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Directory") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showNameError = false },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = showNameError
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = parentPath.ifEmpty { "(root)" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Parent") },
                        trailingIcon = { Text("▾") },
                        modifier = Modifier.fillMaxWidth().clickable { parentExpanded = true }
                    )
                    DropdownMenu(expanded = parentExpanded, onDismissRequest = { parentExpanded = false }) {
                        DropdownMenuItem(text = { Text("(root)") },
                            onClick = { parentPath = ""; parentExpanded = false })
                        parentOptions.forEach { p ->
                            DropdownMenuItem(text = { Text(p) },
                                onClick = { parentPath = p; parentExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) { showNameError = true; return@Button }
                onCreate(name.trim(), parentPath)
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
