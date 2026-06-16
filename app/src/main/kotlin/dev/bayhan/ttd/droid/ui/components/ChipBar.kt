package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.bayhan.ttd.droid.R

@Composable
fun ChipBar(
    projects: List<String>, contexts: List<String>,
    selectedProjects: Set<String>, selectedContexts: Set<String>,
    onToggleProject: (String) -> Unit, onToggleContext: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (projects.isEmpty() && contexts.isEmpty()) return

    val hasFilters = selectedProjects.isNotEmpty() || selectedContexts.isNotEmpty()

    Row(
        modifier = modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasFilters) {
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chip_reset_filters), modifier = Modifier.size(18.dp))
                }
            }

            for (project in projects) {
                val selected = project in selectedProjects
                FilterChip(selected = selected, onClick = { onToggleProject(project) },
                    label = { Text("+$project") })
            }

            if (projects.isNotEmpty() && contexts.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
            }

            for (context in contexts) {
                val selected = context in selectedContexts
                FilterChip(selected = selected, onClick = { onToggleContext(context) },
                    label = { Text("@$context") })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    currentField: String,
    currentAsc: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String, Boolean) -> Unit
) {
    val fields = listOf(
        "default" to stringResource(R.string.chip_sort_default),
        "priority" to stringResource(R.string.chip_sort_priority),
        "date" to stringResource(R.string.chip_sort_date),
        "description" to stringResource(R.string.chip_sort_description)
    )
    var selectedField by remember { mutableStateOf(currentField) }
    var selectedAsc by remember { mutableStateOf(currentAsc) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.chip_sort_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp))

            Text(stringResource(R.string.chip_sort_by),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.selectableGroup()) {
                for ((field, label) in fields) {
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = selectedField == field,
                            onClick = { selectedField = field },
                            role = Role.RadioButton
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedField == field, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (selectedField != "default") {
                Spacer(modifier = Modifier.height(20.dp))
                Text(stringResource(R.string.chip_sort_direction),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chip_sort_ascending),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable { selectedAsc = true }.padding(vertical = 8.dp, horizontal = 4.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = !selectedAsc, onCheckedChange = { selectedAsc = !it })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.chip_sort_descending),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable { selectedAsc = false }.padding(vertical = 8.dp, horizontal = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { onSelect(selectedField, selectedAsc) },
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chip_apply))
            }
        }
    }
}
