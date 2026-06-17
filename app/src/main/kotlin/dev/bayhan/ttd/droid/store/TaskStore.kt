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
                            val list = SmartListParser.parse(raw)
                            LoadedSmartList(path, raw, list, fileName)
                        }
                        2 -> {
                            val path = parts[0]
                            val raw = String(android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP))
                            val list = SmartListParser.parse(raw)
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
