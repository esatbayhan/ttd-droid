package dev.bayhan.ttd.droid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.bayhan.ttd.droid.config.AppConfig
import dev.bayhan.ttd.droid.config.NotifyConfig
import dev.bayhan.ttd.droid.notify.TaskNotifier
import dev.bayhan.ttd.droid.smartlist.LoadedSmartList
import dev.bayhan.ttd.droid.smartlist.SmartListLoader
import dev.bayhan.ttd.droid.store.TaskStore
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.task.TaskParser
import dev.bayhan.ttd.droid.ui.MainScreen
import dev.bayhan.ttd.droid.ui.replaceUpdatedDate
import dev.bayhan.ttd.droid.ui.theme.TtdDroidTheme
import dev.bayhan.ttd.droid.widget.TaskWidgetProvider
import dev.bayhan.ttd.droid.widget.WidgetUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private lateinit var store: TaskStore
    private var isConfigured by mutableStateOf(false)
    private var tasks by mutableStateOf<List<Task>>(emptyList())
    private var smartLists by mutableStateOf<List<LoadedSmartList>>(emptyList())
    private var includeDoneTasks by mutableStateOf(false)
    private var autoPrefillView by mutableStateOf(false)
    private var showFullTaskText by mutableStateOf(false)
    private var hideDateValuesInEditor by mutableStateOf(false)
    private var hideDateValuesInList by mutableStateOf(false)
    private var notifyDue by mutableStateOf(NotifyConfig(false, 9, 0))
    private var notifyScheduled by mutableStateOf(NotifyConfig(false, 7, 0))
    private var notifyStarting by mutableStateOf(NotifyConfig(false, 10, 0))
    private var pendingEditorFilename by mutableStateOf<String?>(null)
    private var showTaskCounts by mutableStateOf(true)
    private var hideUpdatedDate by mutableStateOf(true)
    private var ready by mutableStateOf(false)

    private val dirPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            AppConfig.setTaskDirUri(this, uri)
            store.setRoot(uri)
            isConfigured = true
            refresh()
            WidgetUpdateWorker.enqueue(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        store = TaskStore(this)
        isConfigured = AppConfig.isConfigured(this)
        TaskNotifier.createChannel(this)
        TaskNotifier.schedule(this)
        WidgetUpdateWorker.enqueue(this)

        val savedUri = AppConfig.getTaskDirUri(this)
        autoPrefillView = AppConfig.getAutoPrefillView(this)
        showFullTaskText = AppConfig.getShowFullTaskText(this)
        hideDateValuesInEditor = AppConfig.getHideDateValuesInEditor(this)
        hideDateValuesInList = AppConfig.getHideDateValuesInList(this)
        notifyDue = AppConfig.getNotifyDue(this)
        notifyScheduled = AppConfig.getNotifyScheduled(this)
        notifyStarting = AppConfig.getNotifyStarting(this)
        showTaskCounts = AppConfig.getShowTaskCounts(this)
        hideUpdatedDate = AppConfig.getHideUpdatedDate(this)
        if (savedUri != null) {
            store.setRoot(savedUri)
            tasks = store.loadCachedTasks()
            smartLists = store.loadCachedSmartLists()
            ready = tasks.isNotEmpty() || smartLists.isNotEmpty()
            refresh()
        }

        setContent {
            TtdDroidTheme {
                if (!isConfigured) {
                    SetupScreen(onChooseDir = { dirPicker.launch(null) })
                } else {
                    MainScreen(
                        tasks = tasks, smartLists = smartLists,
                        loading = !ready,
                        initialEditorFilename = pendingEditorFilename,
                        onEditorOpened = { pendingEditorFilename = null },
                        onMarkDone = { task ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                withContext(NonCancellable) {
                                    val filename = task.filename.ifEmpty {
                                        store.loadTasks(includeDone = includeDoneTasks)
                                            .find { it.raw == task.raw }?.filename ?: ""
                                    }
                                    if (filename.isNotEmpty()) {
                                        store.markDone(filename, task.raw)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    TaskNotifier.schedule(this@MainActivity)
                                    TaskWidgetProvider.updateAllWidgets(this@MainActivity)
                                }
                            }
                        },
                        onRemoveFromList = { task ->
                            tasks = tasks.filter { !(it.filename == task.filename && it.raw == task.raw) }
                        },
                        onSaveTask = { raw ->
                            val filename = "task-${android.os.Process.myPid()}-${System.nanoTime()}.txt"
                            tasks = tasks + TaskParser.parse(raw).copy(filename = filename)
                            lifecycleScope.launch(Dispatchers.IO) {
                                withContext(NonCancellable) {
                                    store.createTaskWithFilename(filename, raw)
                                }
                                withContext(Dispatchers.Main) {
                                    TaskNotifier.schedule(this@MainActivity)
                                    TaskWidgetProvider.updateAllWidgets(this@MainActivity)
                                }
                            }
                        },
                        onEditTask = { filename, oldRaw, newRaw ->
                            tasks = tasks.map { t ->
                                if (t.filename == filename && t.raw == oldRaw) TaskParser.parse(newRaw).copy(filename = filename)
                                else t
                            }
                            lifecycleScope.launch(Dispatchers.IO) {
                                withContext(NonCancellable) {
                                    store.overwriteTask(filename, oldRaw, newRaw)
                                }
                                withContext(Dispatchers.Main) {
                                    TaskNotifier.schedule(this@MainActivity)
                                    TaskWidgetProvider.updateAllWidgets(this@MainActivity)
                                }
                            }
                        },
                        onDeleteTask = { task ->
                            tasks = tasks.filter { !(it.filename == task.filename && it.raw == task.raw) }
                            lifecycleScope.launch(Dispatchers.IO) {
                                withContext(NonCancellable) {
                                    store.delete(task.filename, task.raw)
                                }
                                withContext(Dispatchers.Main) {
                                    TaskNotifier.schedule(this@MainActivity)
                                    TaskWidgetProvider.updateAllWidgets(this@MainActivity)
                                }
                            }
                        },
                        onUndoDelete = { task ->
                            val filename = "task-${android.os.Process.myPid()}-${System.nanoTime()}.txt"
                            tasks = tasks + TaskParser.parse(task.raw).copy(filename = filename)
                            lifecycleScope.launch(Dispatchers.IO) {
                                withContext(NonCancellable) {
                                    store.createTaskWithFilename(filename, task.raw)
                                }
                                withContext(Dispatchers.Main) {
                                    TaskNotifier.schedule(this@MainActivity)
                                    TaskWidgetProvider.updateAllWidgets(this@MainActivity)
                                }
                            }
                        },
                        onNeedsDoneTasks = { include -> includeDoneTasks = include },
                        onUpdateTime = { task -> updateTimeTag(task) },
                        autoPrefillView = autoPrefillView,
                        onAutoPrefillViewChange = { enabled ->
                            autoPrefillView = enabled
                            AppConfig.setAutoPrefillView(this@MainActivity, enabled)
                        },
                        showFullTaskText = showFullTaskText,
                        onShowFullTaskTextChange = { enabled ->
                            showFullTaskText = enabled
                            AppConfig.setShowFullTaskText(this@MainActivity, enabled)
                        },
                        hideDateValuesInEditor = hideDateValuesInEditor,
                        onHideDateValuesInEditorChange = { enabled ->
                            hideDateValuesInEditor = enabled
                            AppConfig.setHideDateValuesInEditor(this@MainActivity, enabled)
                        },
                        hideDateValuesInList = hideDateValuesInList,
                        onHideDateValuesInListChange = { enabled ->
                            hideDateValuesInList = enabled
                            AppConfig.setHideDateValuesInList(this@MainActivity, enabled)
                        },
                        notifyDue = notifyDue,
                        onNotifyDueChange = { config ->
                            notifyDue = config
                            AppConfig.setNotifyDue(this@MainActivity, config)
                        },
                        notifyScheduled = notifyScheduled,
                        onNotifyScheduledChange = { config ->
                            notifyScheduled = config
                            AppConfig.setNotifyScheduled(this@MainActivity, config)
                        },
                        notifyStarting = notifyStarting,
                        onNotifyStartingChange = { config ->
                            notifyStarting = config
                            AppConfig.setNotifyStarting(this@MainActivity, config)
                        },
                        showTaskCounts = showTaskCounts,
                        onShowTaskCountsChange = { enabled ->
                            showTaskCounts = enabled
                            AppConfig.setShowTaskCounts(this@MainActivity, enabled)
                        },
                        hideUpdatedDate = hideUpdatedDate,
                        onHideUpdatedDateChange = { enabled ->
                            hideUpdatedDate = enabled
                            AppConfig.setHideUpdatedDate(this@MainActivity, enabled)
                        },
                        onTaskDirChanged = { uri ->
                            store.setRoot(uri)
                            isConfigured = true
                            refresh()
                            WidgetUpdateWorker.enqueue(this@MainActivity)
                        },
                        onSaveSmartList = { filename, groupPath, raw ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                store.createSmartList(groupPath, filename, raw)
                                refresh()
                            }
                        },
                        onDeleteSmartList = { groupPath, filename ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                store.deleteSmartList(groupPath, filename)
                                refresh()
                            }
                        },
                        onCreateDirectory = { name, parentPath ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                store.createListDir(parentPath, name)
                                refresh()
                            }
                        },
                        onDeleteDirectory = { groupPath, dirName ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                store.deleteListDir(groupPath, dirName)
                                refresh()
                            }
                        }
                    )
                }
            }
        }
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (store.isReady()) refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        val taskFilename = intent.getStringExtra("notification_task_filename") ?: return
        pendingEditorFilename = taskFilename
    }

    private fun updateTimeTag(task: Task) {
        val today = LocalDate.now().toString()
        val newRaw = replaceUpdatedDate(task.raw, today)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                store.overwriteTask(task.filename, task.raw, newRaw)
            }
            refresh()
            TaskNotifier.schedule(this@MainActivity)
            TaskWidgetProvider.updateAllWidgets(this@MainActivity)
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                val loadedTasks: List<Task>
                val loadedLists: List<LoadedSmartList>
                withContext(Dispatchers.IO) {
                    store.refreshSnapshot()
                    loadedTasks = store.loadTasks(includeDone = includeDoneTasks)
                    store.saveCache(loadedTasks)
                    val listsDir = store.loadSmartListsDir()
                    if (listsDir != null) {
                        val rawLists = SmartListLoader.load(listsDir, this@MainActivity)
                        store.saveSmartListCache(rawLists)
                        loadedLists = rawLists
                    } else {
                        loadedLists = emptyList()
                    }
                }
                tasks = loadedTasks
                smartLists = loadedLists
            } catch (_: Exception) {}
            ready = true
        }
    }
}

@Composable
fun SetupScreen(onChooseDir: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.setup_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(32.dp))
            Text(stringResource(R.string.setup_instruction),
                style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onChooseDir) { Text(stringResource(R.string.choose_directory)) }
        }
    }
}
