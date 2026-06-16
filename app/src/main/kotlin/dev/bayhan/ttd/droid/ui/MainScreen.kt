package dev.bayhan.ttd.droid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
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
import dev.bayhan.ttd.droid.smartlist.SmartListEval
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.ui.components.SmartListSourceView
import dev.bayhan.ttd.droid.ui.components.SortSheet
import kotlinx.coroutines.launch

sealed interface DrawerItem {
    data class SmartList(val name: String, val group: String?, val fileName: String, val list: dev.bayhan.ttd.droid.smartlist.SmartList) : DrawerItem
    data class Directory(val path: String, val name: String) : DrawerItem
    data class Project(val name: String) : DrawerItem
    data class Context(val name: String) : DrawerItem
    data object Settings : DrawerItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    tasks: List<Task>, smartLists: List<LoadedSmartList>,
    onMarkDone: (Task) -> Unit,
    onSaveTask: (String) -> Unit,
    onEditTask: (String, String) -> Unit,
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
    onHideUpdatedDateChange: (Boolean) -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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
    var highlightFilename by remember { mutableStateOf<String?>(null) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    var projectsExpanded by remember { mutableStateOf(true) }
    var contextsExpanded by remember { mutableStateOf(true) }
    var showSmartListSource by remember { mutableStateOf(false) }
    var sortField by remember { mutableStateOf("default") }
    var sortAsc by remember { mutableStateOf(true) }
    var showSortSheet by remember { mutableStateOf(false) }

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

    LaunchedEffect(highlightFilename) {
        if (highlightFilename != null) {
            kotlinx.coroutines.delay(2000L)
            highlightFilename = null
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.nav_tasks),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))

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
                                        Text(
                                            "${count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                IconButton(onClick = {
                                    expandedGroups[group] = !isExpanded
                                }) {
                                    Icon(
                                        if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                        contentDescription = if (isExpanded) stringResource(R.string.nav_collapse) else stringResource(R.string.nav_expand)
                                    )
                                }
                            },
                            label = { Text(group) },
                            badge = {
                                val count = countTasksInDir(group, smartLists, tasks)
                                Text(
                                    "${count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                IconButton(onClick = { projectsExpanded = !projectsExpanded }) {
                                    Icon(
                                        if (projectsExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                        contentDescription = if (projectsExpanded) stringResource(R.string.nav_collapse) else stringResource(R.string.nav_expand)
                                    )
                            }
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
                                IconButton(onClick = { contextsExpanded = !contextsExpanded }) {
                                    Icon(
                                        if (contextsExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                        contentDescription = if (contextsExpanded) stringResource(R.string.nav_collapse) else stringResource(R.string.nav_expand)
                                    )
                            }
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
                                IconButton(onClick = { showSmartListSource = !showSmartListSource }) {
                                    Icon(
                                        Icons.Default.Code,
                                        contentDescription = if (showSmartListSource) stringResource(R.string.action_show_tasks) else stringResource(R.string.action_show_list_source)
                                    )
                                }
                            }
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(
                                    Icons.Default.Sort,
                                    contentDescription = stringResource(R.string.chip_sort),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (sortField != "default") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!showSettings) {
                    FloatingActionButton(onClick = {
                        val viewPrefill = when {
                            autoPrefillView && selectedItem is DrawerItem.Project -> "+${(selectedItem as DrawerItem.Project).name}"
                            autoPrefillView && selectedItem is DrawerItem.Context -> "@${(selectedItem as DrawerItem.Context).name}"
                            else -> null
                        }
                        val listPrefill = smartListPrefill.joinToString(" ") { (field, value) ->
                            when (field) {
                                "project" -> "+$value"
                                "context" -> "@$value"
                                "due" -> "due:$value"
                                "scheduled" -> "scheduled:$value"
                                else -> ""
                            }
                        }.trim()
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
                    onHideUpdatedDateChange = onHideUpdatedDateChange
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
                    onUpdateTime = onUpdateTime,
                    modifier = Modifier.padding(padding),
                    showFullTaskText = showFullTaskText,
                    hideDateValues = hideDateValuesInList,
                    hideUpdatedDate = hideUpdatedDate,
                    highlightFilename = highlightFilename,
                    groupDirectives = groupDirectives,
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
                    is EditorMode.Edit -> onEditTask(mode.filename, text)
                }
                editorMode = null
            },
            onDismiss = { editorMode = null },
            hideDateValues = hideDateValuesInEditor,
            hideUpdatedDate = hideUpdatedDate,
            smartLists = smartLists,
            onNavigateToItem = { item ->
                val filename = (editorMode as? EditorMode.Edit)?.filename
                editorMode = null
                selectedItem = item
                showSettings = false
                highlightFilename = filename
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
}

private fun countTasksInDir(path: String, smartLists: List<LoadedSmartList>, tasks: List<dev.bayhan.ttd.droid.task.Task>): Int {
    val dirLists = smartLists.filter { it.group == path || it.group.startsWith("$path/") }
        .map { it.list }
    return tasks.count { task -> dirLists.any { SmartListEval.matches(task, it) } }
}
