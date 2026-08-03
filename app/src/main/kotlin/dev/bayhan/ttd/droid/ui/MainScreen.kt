package dev.bayhan.ttd.droid.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bayhan.ttd.droid.R
import dev.bayhan.ttd.droid.config.NotifyConfig
import dev.bayhan.ttd.droid.smartlist.LoadedSmartList
import dev.bayhan.ttd.droid.smartlist.Prefill
import dev.bayhan.ttd.droid.smartlist.SmartListEval
import dev.bayhan.ttd.droid.smartlist.SmartListParser
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.ui.components.DeleteConfirmationDialog
import dev.bayhan.ttd.droid.ui.components.NewDirectoryDialog
import dev.bayhan.ttd.droid.ui.components.SmartListEditorSheet
import dev.bayhan.ttd.droid.ui.components.SmartListSourceView
import dev.bayhan.ttd.droid.ui.components.SortSheet
import kotlinx.coroutines.launch

sealed class SmartListEditorMode {
    data class Create(val defaultGroup: String = "") : SmartListEditorMode()
    data class Edit(val groupPath: String, val filename: String, val raw: String, val list: dev.bayhan.ttd.droid.smartlist.SmartList) : SmartListEditorMode()
}

sealed class DeleteTarget {
    data class SmartList(val groupPath: String, val filename: String, val name: String) : DeleteTarget()
    data class Directory(val path: String, val name: String) : DeleteTarget()
}

sealed interface DrawerItem {
    data class SmartList(val name: String, val group: String?, val fileName: String, val list: dev.bayhan.ttd.droid.smartlist.SmartList) : DrawerItem
    data class Directory(val path: String, val name: String) : DrawerItem
    data class Project(val name: String) : DrawerItem
    data class Context(val name: String) : DrawerItem
    data object Settings : DrawerItem
}

private fun DrawerItem?.taskListViewKey(): String = when (this) {
    is DrawerItem.SmartList -> "smart-list:${group.orEmpty()}/$fileName"
    is DrawerItem.Directory -> "directory:$path"
    is DrawerItem.Project -> "project:$name"
    is DrawerItem.Context -> "context:$name"
    DrawerItem.Settings -> "settings"
    null -> "tasks"
}

fun buildSmartListPrefill(prefills: List<Prefill>): String {
    val priority = prefills.firstOrNull { it.field == "priority" }?.let { "(${it.value})" }
    val remaining = prefills.mapNotNull { (field, value) ->
        when (field) {
            "project" -> "+$value"
            "context" -> "@$value"
            "due", "scheduled", "starting" ->
                SmartListParser.resolveDateValue(value)?.let { "$field:$it" }
            else -> null
        }
    }
    return listOfNotNull(priority).plus(remaining).joinToString(" ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    tasks: List<Task>, smartLists: List<LoadedSmartList>,
    onMarkDone: (Task) -> Unit,
    onSaveTask: (String) -> Unit,
    onEditTask: (String, String, String) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onUndoDelete: (Task) -> Unit,
    onRemoveFromList: (Task) -> Unit = {},
    onNeedsDoneTasks: ((Boolean) -> Unit)? = null,
    onUpdateTime: (Task) -> Unit = {},
    autoPrefillView: Boolean = true,
    onAutoPrefillViewChange: (Boolean) -> Unit = {},
    showFullTaskText: Boolean = false,
    onShowFullTaskTextChange: (Boolean) -> Unit = {},
    hideDateValuesInEditor: Boolean = true,
    onHideDateValuesInEditorChange: (Boolean) -> Unit = {},
    hideDateValuesInList: Boolean = true,
    onHideDateValuesInListChange: (Boolean) -> Unit = {},
    loading: Boolean = false,
    initialEditorFilename: String? = null,
    onEditorOpened: () -> Unit = {},
    notifyDue: NotifyConfig = NotifyConfig(false, 9, 0),
    onNotifyDueChange: (NotifyConfig) -> Unit = {},
    notifyScheduled: NotifyConfig = NotifyConfig(false, 7, 0),
    onNotifyScheduledChange: (NotifyConfig) -> Unit = {},
    notifyStarting: NotifyConfig = NotifyConfig(false, 10, 0),
    onNotifyStartingChange: (NotifyConfig) -> Unit = {},
    showTaskCounts: Boolean = true,
    onShowTaskCountsChange: (Boolean) -> Unit = {},
    hideUpdatedDate: Boolean = true,
    onHideUpdatedDateChange: (Boolean) -> Unit = {},
    onTaskDirChanged: ((Uri) -> Unit)? = null,
    onSaveSmartList: (filename: String, groupPath: String, raw: String) -> Unit = { _, _, _ -> },
    onDeleteSmartList: (groupPath: String, filename: String) -> Unit = { _, _ -> },
    onCreateDirectory: (name: String, parentPath: String) -> Unit = { _, _ -> },
    onDeleteDirectory: (groupPath: String, dirName: String) -> Unit = { _, _ -> }
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val allProjects = remember(tasks) { tasks.flatMap { it.projects }.distinct().sorted() }
    val allContexts = remember(tasks) { tasks.flatMap { it.contexts }.distinct().sorted() }

    var selectedItem by remember {
        val g = smartLists.groupBy({ it.group }, { it }).toSortedMap()
        val topDirs = g.keys.filter { it.isNotEmpty() }.map { it.split("/").first() }.distinct().sorted()
        val rootLists = (g[""] ?: emptyList()).sortedBy { it.fileName }
        mutableStateOf<DrawerItem?>(
            rootLists.firstOrNull()?.let { DrawerItem.SmartList(it.list.name, null, it.fileName, it.list) }
                ?: topDirs.firstOrNull()?.let { DrawerItem.Directory(it, it) }
                ?: allProjects.firstOrNull()?.let { DrawerItem.Project(it) }
                ?: allContexts.firstOrNull()?.let { DrawerItem.Context(it) }
        )
    }
    var autoSelectDone by remember { mutableStateOf(selectedItem != null) }
    var showSettings by remember { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf<EditorMode?>(null) }
    var highlightTaskKey by remember { mutableStateOf<String?>(null) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    var projectsExpanded by remember { mutableStateOf(true) }
    var contextsExpanded by remember { mutableStateOf(true) }
    var showSmartListSource by remember { mutableStateOf(false) }
    var sortField by remember { mutableStateOf("default") }
    var sortAsc by remember { mutableStateOf(true) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var listEditorMode by remember { mutableStateOf<SmartListEditorMode?>(null) }
    var showNewDirectoryDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }
    var selectedGroupForCreate by remember { mutableStateOf("") }
    var collisionPending by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val groupedSmartLists = remember(smartLists) {
        smartLists.groupBy({ it.group }, { it }).toSortedMap()
    }

    val topLevelGroups = remember(groupedSmartLists) {
        groupedSmartLists.keys.filter { it.isNotEmpty() }
            .map { it.split("/").first() }.distinct().sorted()
    }

    val totalCount = remember(tasks) { tasks.size }

    LaunchedEffect(smartLists) {
        if (autoSelectDone) return@LaunchedEffect

        val rootLists = (groupedSmartLists[""] ?: emptyList()).sortedBy { it.fileName }
        val firstRoot = rootLists.firstOrNull()
        if (firstRoot != null) {
            selectedItem = DrawerItem.SmartList(firstRoot.list.name, null, firstRoot.fileName, firstRoot.list)
            autoSelectDone = true
            return@LaunchedEffect
        }
        val firstDir = topLevelGroups.firstOrNull()
        if (firstDir != null) {
            selectedItem = DrawerItem.Directory(firstDir, firstDir)
            autoSelectDone = true
            return@LaunchedEffect
        }
        if (selectedItem == null) {
            val firstProj = allProjects.firstOrNull()
            if (firstProj != null) {
                selectedItem = DrawerItem.Project(firstProj)
                autoSelectDone = true
                return@LaunchedEffect
            }
            val firstCtx = allContexts.firstOrNull()
            if (firstCtx != null) {
                selectedItem = DrawerItem.Context(firstCtx)
                autoSelectDone = true
            }
        }
    }

    val filteredTasks = remember(tasks, selectedItem, smartLists) {
        when (val item = selectedItem) {
            is DrawerItem.SmartList -> tasks.filter { SmartListEval.matches(it, item.list) }
            is DrawerItem.Directory -> {
                val dirLists = smartLists.filter { it.group == item.path || it.group.startsWith("${item.path}/") }
                    .map { it.list }
                tasks.filter { task -> dirLists.any { SmartListEval.matches(task, it) } }
            }
            is DrawerItem.Project -> tasks.filter { it.projects.contains(item.name) }
            is DrawerItem.Context -> tasks.filter { it.contexts.contains(item.name) }
            null -> tasks
            else -> tasks
        }
    }

    val groupDirectives = remember(selectedItem) {
        when (val item = selectedItem) {
            is DrawerItem.SmartList -> item.list.groups
            else -> emptyList()
        }
    }

    val sortDirectives = remember(selectedItem) {
        when (val item = selectedItem) {
            is DrawerItem.SmartList -> item.list.sorts
            else -> emptyList()
        }
    }

    val title = when (val item = selectedItem) {
        is DrawerItem.SmartList -> {
            val icon = item.list.icon
            if (icon != null) "$icon ${item.name}" else item.name
        }
        is DrawerItem.Directory -> item.name
        is DrawerItem.Project -> item.name
        is DrawerItem.Context -> item.name
        null -> stringResource(R.string.nav_tasks)
        else -> stringResource(R.string.nav_settings)
    }

    val displayTitle = if (showTaskCounts && selectedItem != null && selectedItem !is DrawerItem.Settings) {
        "$title (${filteredTasks.size})"
    } else {
        title
    }

    val smartListPrefill = remember(selectedItem) {
        when (val item = selectedItem) {
            is DrawerItem.SmartList -> item.list.prefills
            else -> emptyList()
        }
    }

    val needsDoneTasks = remember(selectedItem) {
        when (val item = selectedItem) {
            is DrawerItem.SmartList -> item.list.conditions.any { block ->
                block.conditions.any { it is dev.bayhan.ttd.droid.smartlist.DoneCondition && it.done }
            }
            else -> false
        }
    }

    LaunchedEffect(needsDoneTasks) {
        onNeedsDoneTasks?.invoke(needsDoneTasks)
    }

    LaunchedEffect(highlightTaskKey) {
        if (highlightTaskKey != null) {
            kotlinx.coroutines.delay(2000L)
            highlightTaskKey = null
        }
    }

    LaunchedEffect(selectedItem) {
        if (selectedItem !is DrawerItem.SmartList) {
            showSmartListSource = false
        }
    }

    LaunchedEffect(initialEditorFilename, tasks) {
        if (initialEditorFilename != null) {
            val task = tasks.find { it.filename == initialEditorFilename }
            if (task != null) {
                editorMode = EditorMode.Edit(task.filename, task.raw)
                onEditorOpened()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.nav_tasks),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(vertical = 8.dp))
                        Box {
                            IconButton(onClick = { showCreateMenu = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Create",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showCreateMenu,
                                onDismissRequest = { showCreateMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Smart List") },
                                    onClick = {
                                        showCreateMenu = false
                                        selectedGroupForCreate = when {
                                            selectedItem is DrawerItem.SmartList ->
                                                (selectedItem as DrawerItem.SmartList).group ?: ""
                                            selectedItem is DrawerItem.Directory ->
                                                (selectedItem as DrawerItem.Directory).path
                                            else -> ""
                                        }
                                        listEditorMode = SmartListEditorMode.Create(selectedGroupForCreate)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("New Directory") },
                                    onClick = {
                                        showCreateMenu = false
                                        selectedGroupForCreate = when {
                                            selectedItem is DrawerItem.Directory ->
                                                (selectedItem as DrawerItem.Directory).path
                                            else -> ""
                                        }
                                        showNewDirectoryDialog = true
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))

                    val rootLists = (groupedSmartLists[""] ?: emptyList()).sortedBy { it.fileName }
                    for (loaded in rootLists) {
                        NavigationDrawerItem(
                            selected = selectedItem is DrawerItem.SmartList
                                && (selectedItem as DrawerItem.SmartList).name == loaded.list.name
                                && (selectedItem as DrawerItem.SmartList).group == null
                                && !showSettings,
                            onClick = {
                                selectedItem = DrawerItem.SmartList(loaded.list.name, null, loaded.fileName, loaded.list)
                                showSettings = false
                                scope.launch { drawerState.close() }
                            },
                            icon = {
                                val iconStr = loaded.list.icon
                                if (iconStr != null) {
                                    Text(iconStr)
                                } else {
                                    Icon(Icons.Default.Menu, contentDescription = loaded.list.name)
                                }
                            },
                            label = { Text(loaded.list.name) },
                            badge = {
                                val count = tasks.count { SmartListEval.matches(it, loaded.list) }
                                Text("${count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    if (groupedSmartLists[""] != null && topLevelGroups.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                    }

                    for (group in topLevelGroups) {
                        val isExpanded = expandedGroups[group] == true
                        val isSelected = selectedItem is DrawerItem.Directory && (selectedItem as DrawerItem.Directory).path == group

                        NavigationDrawerItem(
                            selected = isSelected && !showSettings,
                            onClick = {
                                selectedItem = DrawerItem.Directory(group, group)
                                showSettings = false
                                scope.launch { drawerState.close() }
                            },
                            icon = {
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    contentDescription = if (isExpanded) stringResource(R.string.nav_collapse) else stringResource(R.string.nav_expand),
                                    modifier = Modifier.clickable {
                                        expandedGroups[group] = !isExpanded
                                    }
                                )
                            },
                            label = { Text(group) },
                            badge = {
                                val count = countTasksInDir(group, smartLists, tasks)
                                Text("${count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        if (isExpanded) {
                            for ((subGroup, lists) in groupedSmartLists) {
                                if (subGroup == group || subGroup.startsWith("$group/")) {
                                    for (loaded in lists.sortedBy { it.fileName }) {
                                            NavigationDrawerItem(
                                                selected = selectedItem is DrawerItem.SmartList
                                                    && (selectedItem as DrawerItem.SmartList).name == loaded.list.name
                                                    && (selectedItem as DrawerItem.SmartList).group == subGroup
                                                    && !showSettings,
                                                onClick = {
                                                    selectedItem = DrawerItem.SmartList(loaded.list.name, subGroup, loaded.fileName, loaded.list)
                                                    showSettings = false
                                                    scope.launch { drawerState.close() }
                                                },
                                                icon = {
                                                    val iconStr = loaded.list.icon
                                                    if (iconStr != null) {
                                                        Text(iconStr)
                                                    } else {
                                                        Icon(Icons.Default.Menu, contentDescription = loaded.list.name)
                                                    }
                                                },
                                                label = { Text(loaded.list.name) },
                                                badge = {
                                                    val count = tasks.count { SmartListEval.matches(it, loaded.list) }
                                                    Text("${count}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                },
                                                modifier = Modifier.padding(start = 28.dp, end = 12.dp)
                                            )
                                    }
                                }
                            }
                        }
                    }

                    if (topLevelGroups.isNotEmpty() && allProjects.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                    }

                    if (allProjects.isNotEmpty()) {
                        NavigationDrawerItem(
                            selected = false,
                            onClick = { projectsExpanded = !projectsExpanded },
                            icon = {
                                    Icon(
                                        if (projectsExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                        contentDescription = if (projectsExpanded) stringResource(R.string.nav_collapse) else stringResource(R.string.nav_expand),
                                        modifier = Modifier.clickable { projectsExpanded = !projectsExpanded }
                                    )
                            },
                        label = { Text(stringResource(R.string.nav_projects)) },
                        badge = {
                            Text("${allProjects.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    if (projectsExpanded) {
                            for (project in allProjects) {
                                NavigationDrawerItem(
                                    selected = selectedItem is DrawerItem.Project
                                        && (selectedItem as DrawerItem.Project).name == project
                                        && !showSettings,
                                    onClick = {
                                        selectedItem = DrawerItem.Project(project)
                                        showSettings = false
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Text("+", style = MaterialTheme.typography.bodyMedium) },
                                    label = { Text(project) },
                                    badge = {
                                        val count = tasks.count { it.projects.contains(project) }
                                        Text("${count}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    },
                                    modifier = Modifier.padding(start = 28.dp, end = 12.dp)
                                )
                            }
                        }
                    }

                    if ((allProjects.isNotEmpty() || topLevelGroups.isNotEmpty()) && allContexts.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                    }

                    if (allContexts.isNotEmpty()) {
                        NavigationDrawerItem(
                            selected = false,
                            onClick = { contextsExpanded = !contextsExpanded },
                            icon = {
                                    Icon(
                                        if (contextsExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                        contentDescription = if (contextsExpanded) stringResource(R.string.nav_collapse) else stringResource(R.string.nav_expand),
                                        modifier = Modifier.clickable { contextsExpanded = !contextsExpanded }
                                    )
                            },
                        label = { Text(stringResource(R.string.nav_contexts)) },
                        badge = {
                            Text("${allContexts.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    if (contextsExpanded) {
                            for (context in allContexts) {
                                NavigationDrawerItem(
                                    selected = selectedItem is DrawerItem.Context
                                        && (selectedItem as DrawerItem.Context).name == context
                                        && !showSettings,
                                    onClick = {
                                        selectedItem = DrawerItem.Context(context)
                                        showSettings = false
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Text("@", style = MaterialTheme.typography.bodyMedium) },
                                    label = { Text(context) },
                                    badge = {
                                        val count = tasks.count { it.contexts.contains(context) }
                                        Text("${count}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    },
                                    modifier = Modifier.padding(start = 28.dp, end = 12.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))

                    NavigationDrawerItem(
                        selected = showSettings,
                        onClick = { showSettings = true; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        pluralStringResource(R.plurals.task_count, totalCount, totalCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                }
        }
    ) {
        Scaffold(
            topBar = {
                if (showSettings) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.nav_settings)) },
                        navigationIcon = {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(displayTitle) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_open_drawer))
                            }
                        },
                        actions = {
                            if (selectedItem is DrawerItem.SmartList) {
                                val smartItem = selectedItem as DrawerItem.SmartList
                                val loaded = smartLists.find {
                                    it.fileName == smartItem.fileName && it.group == (smartItem.group ?: "")
                                }
                                if (loaded != null) {
                                    IconButton(onClick = {
                                        listEditorMode = SmartListEditorMode.Edit(
                                            loaded.group, loaded.fileName, loaded.raw, loaded.list
                                        )
                                    }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.action_edit_list)
                                        )
                                    }
                                    IconButton(onClick = {
                                        deleteTarget = DeleteTarget.SmartList(
                                            loaded.group, loaded.fileName, loaded.list.name
                                        )
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete_list)
                                        )
                                    }
                                }
                                IconButton(onClick = { showSmartListSource = !showSmartListSource }) {
                                    Icon(
                                        Icons.Default.Code,
                                        contentDescription = if (showSmartListSource) stringResource(R.string.action_show_tasks) else stringResource(R.string.action_show_list_source)
                                    )
                                }
                            }
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.chip_sort),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (sortField != "default") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            floatingActionButton = {
                if (!showSettings) {
                    FloatingActionButton(onClick = {
                        val viewPrefill = when {
                            autoPrefillView && selectedItem is DrawerItem.Project -> "+${(selectedItem as DrawerItem.Project).name}"
                            autoPrefillView && selectedItem is DrawerItem.Context -> "@${(selectedItem as DrawerItem.Context).name}"
                            else -> null
                        }
                        val listPrefill = buildSmartListPrefill(smartListPrefill)
                        val combined = listOfNotNull(viewPrefill, listPrefill.ifEmpty { null })
                            .joinToString(" ").trim()
                        editorMode = EditorMode.Add(prefill = combined.ifEmpty { null })
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_task))
                    }
                }
            }
        ) { padding ->
            if (showSettings) {
                SettingsContent(
                    modifier = Modifier.padding(padding),
                    autoPrefillView = autoPrefillView,
                    onAutoPrefillViewChange = onAutoPrefillViewChange,
                    showFullTaskText = showFullTaskText,
                    onShowFullTaskTextChange = onShowFullTaskTextChange,
                    hideDateValuesInEditor = hideDateValuesInEditor,
                    onHideDateValuesInEditorChange = onHideDateValuesInEditorChange,
                    hideDateValuesInList = hideDateValuesInList,
                    onHideDateValuesInListChange = onHideDateValuesInListChange,
                    notifyDue = notifyDue,
                    onNotifyDueChange = onNotifyDueChange,
                    notifyScheduled = notifyScheduled,
                    onNotifyScheduledChange = onNotifyScheduledChange,
                    notifyStarting = notifyStarting,
                    onNotifyStartingChange = onNotifyStartingChange,
                    showTaskCounts = showTaskCounts,
                    onShowTaskCountsChange = onShowTaskCountsChange,
                    hideUpdatedDate = hideUpdatedDate,
                    onHideUpdatedDateChange = onHideUpdatedDateChange,
                    onTaskDirChanged = onTaskDirChanged
                )
            } else if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (showSmartListSource && selectedItem is DrawerItem.SmartList) {
                val item = selectedItem as DrawerItem.SmartList
                val raw = smartLists.find {
                    it.fileName == item.fileName && it.group == (item.group ?: "")
                }?.raw ?: ""
                SmartListSourceView(
                    raw = raw,
                    modifier = Modifier.padding(padding)
                )
            } else {
                TaskListContent(
                    tasks = filteredTasks,
                    onMarkDone = onMarkDone,
                    onRemoveFromList = onRemoveFromList,
                    onEditTask = { task -> editorMode = EditorMode.Edit(task.filename, task.raw) },
                    onDeleteTask = onDeleteTask,
                    onUndoDelete = onUndoDelete,
                    snackbarHostState = snackbarHostState,
                    onUpdateTime = onUpdateTime,
                    modifier = Modifier.padding(padding),
                    showFullTaskText = showFullTaskText,
                    hideDateValues = hideDateValuesInList,
                    hideUpdatedDate = hideUpdatedDate,
                    highlightTaskKey = highlightTaskKey,
                    viewKey = selectedItem.taskListViewKey(),
                    groupDirectives = groupDirectives,
                    sortDirectives = sortDirectives,
                    sortField = sortField,
                    sortAsc = sortAsc
                )
            }
        }
    }

    editorMode?.let { mode ->
        TaskEditor(
            editorMode = mode,
            allProjects = allProjects,
            allContexts = allContexts,
            onSave = { text ->
                when (mode) {
                    is EditorMode.Add -> onSaveTask(text)
                    is EditorMode.Edit -> onEditTask(mode.filename, mode.raw, text)
                }
                editorMode = null
            },
            onDismiss = { editorMode = null },
            hideDateValues = hideDateValuesInEditor,
            hideUpdatedDate = hideUpdatedDate,
            smartLists = smartLists,
            onNavigateToItem = { item ->
                val mode = editorMode as? EditorMode.Edit
                val taskKey = mode?.let { "${it.filename}\t${it.raw}" }
                editorMode = null
                selectedItem = item
                showSettings = false
                highlightTaskKey = taskKey
            }
        )
    }

    if (showSortSheet) {
        SortSheet(
            currentField = sortField,
            currentAsc = sortAsc,
            onDismiss = { showSortSheet = false },
            onSelect = { field, asc ->
                sortField = field
                sortAsc = asc
                showSortSheet = false
            }
        )
    }

    listEditorMode?.let { mode ->
        val allGroups = remember(groupedSmartLists) {
            groupedSmartLists.keys.filter { it.isNotEmpty() }.sorted()
        }
        SmartListEditorSheet(
            initialList = when (mode) {
                is SmartListEditorMode.Edit -> mode.list
                is SmartListEditorMode.Create -> null
            },
            initialRaw = when (mode) {
                is SmartListEditorMode.Edit -> mode.raw
                is SmartListEditorMode.Create -> null
            },
            initialFilename = when (mode) {
                is SmartListEditorMode.Edit -> mode.filename
                else -> ""
            },
            groupOptions = allGroups,
            defaultGroup = when (mode) {
                is SmartListEditorMode.Edit -> mode.groupPath
                is SmartListEditorMode.Create -> mode.defaultGroup
            },
            onDismiss = { listEditorMode = null },
            onSave = { filename, groupPath, raw ->
                val isEdit = mode is SmartListEditorMode.Edit
                if (isEdit) {
                    listEditorMode = null
                    onSaveSmartList(filename, groupPath, raw)
                } else {
                    collisionPending = Triple(filename, groupPath, raw)
                }
            }
        )
    }

    if (showNewDirectoryDialog) {
        val allGroups = remember(groupedSmartLists) {
            groupedSmartLists.keys.filter { it.isNotEmpty() }.sorted()
        }
        NewDirectoryDialog(
            parentOptions = allGroups,
            defaultParent = selectedGroupForCreate,
            onDismiss = { showNewDirectoryDialog = false },
            onCreate = { name, parentPath ->
                showNewDirectoryDialog = false
                onCreateDirectory(name, parentPath)
            }
        )
    }

    deleteTarget?.let { target ->
        when (target) {
            is DeleteTarget.SmartList -> DeleteConfirmationDialog(
                itemName = target.name,
                itemPath = "lists.d/${if (target.groupPath.isNotEmpty()) "${target.groupPath}/" else ""}${target.filename}",
                isDirectory = false,
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    deleteTarget = null
                    onDeleteSmartList(target.groupPath, target.filename)
                }
            )
            is DeleteTarget.Directory -> DeleteConfirmationDialog(
                itemName = target.name,
                itemPath = "lists.d/${target.path}/",
                isDirectory = true,
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    deleteTarget = null
                    onDeleteDirectory(target.path, target.name)
                }
            )
        }
    }

    collisionPending?.let { (filename, groupPath, raw) ->
        AlertDialog(
            onDismissRequest = { collisionPending = null },
            title = { Text("File already exists?") },
            text = { Text("A list named \"$filename\" may already exist in this group.\n\nOverwrite it?") },
            confirmButton = {
                Button(
                    onClick = {
                        collisionPending = null
                        listEditorMode = null
                        onSaveSmartList(filename, groupPath, raw)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { collisionPending = null }) { Text("Cancel") }
            }
        )
    }
}

private fun countTasksInDir(path: String, smartLists: List<LoadedSmartList>, tasks: List<dev.bayhan.ttd.droid.task.Task>): Int {
    val dirLists = smartLists.filter { it.group == path || it.group.startsWith("$path/") }
        .map { it.list }
    return tasks.count { task -> dirLists.any { SmartListEval.matches(task, it) } }
}
