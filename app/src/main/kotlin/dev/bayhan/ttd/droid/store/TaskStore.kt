package dev.bayhan.ttd.droid.store

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.task.TaskParser
import dev.bayhan.ttd.droid.smartlist.LoadedSmartList
import dev.bayhan.ttd.droid.smartlist.SmartListParser

class TaskStore(private val context: Context) {

    private var rootUri: Uri? = null
    private var snapshot: SnapshotIndex? = null
    private var rootDoc: DocumentFile? = null

    fun isReady(): Boolean = rootUri != null || rootDoc != null

    fun setRoot(uri: Uri) {
        rootUri = uri
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {}
    }

    fun setRoot(doc: DocumentFile) {
        rootDoc = doc
    }

    fun refreshSnapshot(): RefreshDelta? {
        val doc = rootDoc ?: rootUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        val newSnapshot = SnapshotIndex.build(doc)
        val delta = snapshot?.diff(newSnapshot)
            ?: RefreshDelta(newSnapshot.entries.keys.toList(), emptyList(), emptyList())
        snapshot = newSnapshot
        return delta
    }

    fun loadTasks(includeDone: Boolean = false): List<Task> {
        val doc = rootDoc ?: rootUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return emptyList()
        val tasks = mutableListOf<Task>()
        collectTasks(doc, tasks, includeDone)
        return tasks
    }

    private fun collectTasks(dir: DocumentFile, tasks: MutableList<Task>, includeDone: Boolean = false) {
        val files = try {
            dir.listFiles()
        } catch (_: Exception) { return }

        files.forEach { file ->
            if (file.isDirectory) {
                if (includeDone || file.name != "done.txt.d") {
                    collectTasks(file, tasks, includeDone)
                }
            } else if (file.name?.endsWith(".txt") == true) {
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { stream ->
                        stream.bufferedReader().readLines().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                tasks.add(TaskParser.parse(trimmed).copy(filename = file.name ?: ""))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun createTask(raw: String): String? {
        val uri = rootUri ?: return null
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return null
        val filename = "task-${android.os.Process.myPid()}-${System.nanoTime()}.txt"
        val newFile = rootDoc.createFile("text/plain", filename) ?: return null

        return try {
            context.contentResolver.openOutputStream(newFile.uri)?.use { stream ->
                stream.write(raw.toByteArray())
            }
            refreshSnapshot()
            filename
        } catch (_: Exception) {
            null
        }
    }

    fun createTaskWithFilename(filename: String, raw: String): Boolean {
        val uri = rootUri ?: return false
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return false
        val newFile = rootDoc.createFile("text/plain", filename) ?: return false
        return try {
            context.contentResolver.openOutputStream(newFile.uri)?.use { stream ->
                stream.write(raw.toByteArray())
            }
            refreshSnapshot()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun markDone(filename: String, raw: String): Boolean {
        val uri = rootUri ?: return false
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return false
        val taskFile = findFile(rootDoc, filename) ?: return false

        val lines = try {
            context.contentResolver.openInputStream(taskFile.uri)?.use {
                it.bufferedReader().readLines()
            } ?: return false
        } catch (_: Exception) { return false }

        val trimmedRaw = raw.trim()
        val remaining = lines.filter { it.trim() != trimmedRaw }

        val task = TaskParser.parse(raw)
        val today = java.time.LocalDate.now().toString()
        val doneLine = TaskParser.format(
            task.copy(done = true, completionDate = today, priority = null)
        )

        val doneDir = try {
            rootDoc.listFiles().find { it.name == "done.txt.d" }
        } catch (_: Exception) { null }
            ?: rootDoc.createDirectory("done.txt.d") ?: return false
        val doneFilename = if (remaining.isEmpty()) filename else "done-${android.os.Process.myPid()}-${System.nanoTime()}.txt"
        val destFile = doneDir.createFile("text/plain", doneFilename) ?: return false

        return try {
            context.contentResolver.openOutputStream(destFile.uri)?.use { stream ->
                stream.write(doneLine.toByteArray())
            }

            if (remaining.isEmpty()) {
                taskFile.delete()
            } else {
                context.contentResolver.openOutputStream(taskFile.uri, "wt")?.use { stream ->
                    stream.write(remaining.joinToString("\n").toByteArray())
                }
            }

            refreshSnapshot()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delete(filename: String, raw: String): Boolean {
        val uri = rootUri ?: return false
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return false
        val file = findFile(rootDoc, filename) ?: return false

        val lines = try {
            context.contentResolver.openInputStream(file.uri)?.use {
                it.bufferedReader().readLines()
            } ?: return false
        } catch (_: Exception) { return false }

        val trimmedRaw = raw.trim()
        val remaining = lines.filter { it.trim() != trimmedRaw }

        return try {
            val deleted = if (remaining.isEmpty()) {
                file.delete()
            } else {
                context.contentResolver.openOutputStream(file.uri, "wt")?.use { stream ->
                    stream.write(remaining.joinToString("\n").toByteArray())
                }
                true
            }
            if (deleted) refreshSnapshot()
            deleted
        } catch (_: Exception) { false }
    }

    private fun findFile(dir: DocumentFile, name: String): DocumentFile? {
        val files = try {
            dir.listFiles()
        } catch (_: Exception) { return null }

        files.forEach { file ->
            if (!file.isDirectory && file.name == name) return file
            if (file.isDirectory) {
                val found = findFile(file, name)
                if (found != null) return found
            }
        }
        return null
    }

    fun overwriteTask(filename: String, oldRaw: String, newRaw: String): Boolean {
        val uri = rootUri ?: return false
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return false
        val file = findFile(rootDoc, filename) ?: return false

        val lines = try {
            context.contentResolver.openInputStream(file.uri)?.use {
                it.bufferedReader().readLines()
            } ?: return false
        } catch (_: Exception) { return false }

        val trimmedOld = oldRaw.trim()
        val updated = lines.map { if (it.trim() == trimmedOld) newRaw else it }

        return try {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { stream ->
                stream.write(updated.joinToString("\n").toByteArray())
            }
            refreshSnapshot()
            true
        } catch (_: Exception) { false }
    }

    fun loadSmartListsDir(): DocumentFile? {
        val uri = rootUri ?: return null
        val rootDoc = DocumentFile.fromTreeUri(context, uri) ?: return null
        return try {
            rootDoc.listFiles().find { it.name == "lists.d" }
        } catch (_: Exception) { null }
    }

    fun createSmartList(groupPath: String, filename: String, raw: String): Boolean {
        val fn = normalizedFilename(filename)
        val rootDoc = rootUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return false
        return try {
            val listsDir = getOrCreateListsDir(rootDoc) ?: return false
            val targetDir = navigateOrCreatePath(listsDir, groupPath) ?: return false
            val file = targetDir.createFile("application/octet-stream", fn) ?: return false
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(raw.toByteArray()) }
            refreshSnapshot()
            true
        } catch (_: Exception) { false }
    }

    fun deleteSmartList(groupPath: String, filename: String): Boolean {
        val rootDoc = rootUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return false
        return try {
            val listsDir = rootDoc.listFiles().find { it.name == "lists.d" } ?: return false
            val file = findFileInListsDir(listsDir, groupPath, filename) ?: return false
            val result = file.delete()
            if (result) refreshSnapshot()
            result
        } catch (_: Exception) { false }
    }

    fun createListDir(groupPath: String, dirName: String): Boolean {
        val rootDoc = rootUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return false
        return try {
            val listsDir = getOrCreateListsDir(rootDoc) ?: return false
            val targetDir = navigateOrCreatePath(listsDir, groupPath) ?: return false
            targetDir.createDirectory(dirName) != null
        } catch (_: Exception) { false }
    }

    fun deleteListDir(groupPath: String, dirName: String): Boolean {
        val rootDoc = rootUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return false
        return try {
            val listsDir = rootDoc.listFiles().find { it.name == "lists.d" } ?: return false
            val fullPath = if (groupPath.isEmpty()) dirName else "$groupPath/$dirName"
            val dir = navigatePath(listsDir, fullPath) ?: return false
            val result = dir.delete()
            if (result) refreshSnapshot()
            result
        } catch (_: Exception) { false }
    }

    fun findSmartListFile(groupPath: String, filename: String): DocumentFile? {
        val rootDoc = rootUri?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        return try {
            val listsDir = rootDoc.listFiles().find { it.name == "lists.d" } ?: return null
            findFileInListsDir(listsDir, groupPath, filename)
        } catch (_: Exception) { null }
    }

    fun smartListExists(groupPath: String, filename: String): Boolean {
        return findSmartListFile(groupPath, filename) != null
    }

    private fun getOrCreateListsDir(rootDoc: DocumentFile): DocumentFile? {
        return try {
            rootDoc.listFiles().find { it.name == "lists.d" }
        } catch (_: Exception) { null }
            ?: rootDoc.createDirectory("lists.d")
    }

    private fun navigateOrCreatePath(listsDir: DocumentFile, groupPath: String): DocumentFile? {
        if (groupPath.isEmpty()) return listsDir
        var dir = listsDir
        for (part in groupPath.split("/")) {
            val existing = try {
                dir.listFiles().find { it.isDirectory && it.name == part }
            } catch (_: Exception) { null }
            dir = existing ?: dir.createDirectory(part) ?: return null
        }
        return dir
    }

    private fun navigatePath(listsDir: DocumentFile, groupPath: String): DocumentFile? {
        if (groupPath.isEmpty()) return listsDir
        var dir = listsDir
        for (part in groupPath.split("/")) {
            dir = try {
                dir.listFiles().find { it.isDirectory && it.name == part }
            } catch (_: Exception) { null } ?: return null
        }
        return dir
    }

    private fun normalizedFilename(filename: String): String =
        if (filename.endsWith(".list")) filename else "$filename.list"

    private fun findFileInListsDir(listsDir: DocumentFile, groupPath: String, filename: String): DocumentFile? {
        val dir = navigatePath(listsDir, groupPath) ?: return null
        val fn = normalizedFilename(filename)
        return try {
            dir.listFiles().find { !it.isDirectory && it.name == fn }
        } catch (_: Exception) { null }
    }

    fun loadCachedTasks(): List<Task> {
        val file = java.io.File(context.filesDir, "tasks.cache")
        return try {
            file.readLines()
                .filter { it.isNotBlank() }
                .map { line ->
                    val tabIdx = line.indexOf('\t')
                    if (tabIdx >= 0) {
                        val filename = line.substring(0, tabIdx)
                        val raw = line.substring(tabIdx + 1)
                        TaskParser.parse(raw).copy(filename = filename)
                    } else {
                        TaskParser.parse(line)
                    }
                }
        } catch (_: Exception) { emptyList() }
    }

    fun saveCache(tasks: List<Task>) {
        val file = java.io.File(context.filesDir, "tasks.cache")
        try {
            file.writeText(tasks.joinToString("\n") { "${it.filename}\t${it.raw}" })
        } catch (_: Exception) {}
    }

    fun loadCachedSmartLists(): List<LoadedSmartList> {
        val file = java.io.File(context.filesDir, "smartlists.cache")
        return try {
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    when (parts.size) {
                        3 -> {
                            val path = parts[0]
                            val fileName = parts[1]
                            val raw = String(android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP))
                            val list = SmartListParser.parse(raw, path) ?: return@mapNotNull null
                            LoadedSmartList(path, raw, list, fileName)
                        }
                        2 -> {
                            val path = parts[0]
                            val raw = String(android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP))
                            val list = SmartListParser.parse(raw, path) ?: return@mapNotNull null
                            LoadedSmartList(path, raw, list, list.name)
                        }
                        else -> null
                    }
                }
        } catch (_: Exception) { emptyList() }
    }

    fun saveSmartListCache(lists: List<LoadedSmartList>) {
        val file = java.io.File(context.filesDir, "smartlists.cache")
        try {
            file.writeText(lists.joinToString("\n") { "${it.group}\t${it.fileName}\t${android.util.Base64.encodeToString(it.raw.toByteArray(), android.util.Base64.NO_WRAP)}" })
        } catch (_: Exception) {}
    }
}
