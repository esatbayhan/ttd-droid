package dev.bayhan.ttd.droid.smartlist

import android.content.Context
import androidx.documentfile.provider.DocumentFile

data class LoadedSmartList(
    val group: String,
    val raw: String,
    val list: SmartList,
    val fileName: String
)

object SmartListLoader {

    fun load(listsDir: DocumentFile, context: Context): List<LoadedSmartList> {
        if (!listsDir.isDirectory) return emptyList()

        val results = mutableListOf<LoadedSmartList>()
        collectLists(listsDir, context, "", results)
        return results
    }

    private fun collectLists(
        dir: DocumentFile,
        context: Context,
        groupPath: String,
        results: MutableList<LoadedSmartList>
    ) {
        val files = try {
            dir.listFiles()
        } catch (_: Exception) { return }

        files.filter { !it.isDirectory && it.name?.endsWith(".list") == true }
            .sortedBy { it.name }
            .forEach { file ->
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { stream ->
                        val text = stream.bufferedReader().readText()
                        val list = SmartListParser.parse(text, groupPath) ?: return@use
                        val fileName = file.name?.removeSuffix(".list") ?: "unknown"
                        results.add(LoadedSmartList(groupPath, text, list, fileName))
                    }
                } catch (_: Exception) {}
            }

        files.filter { it.isDirectory }
            .sortedBy { it.name }
            .forEach { subDir ->
                val name = subDir.name ?: return@forEach
                val path = if (groupPath.isEmpty()) name else "$groupPath/$name"
                collectLists(subDir, context, path, results)
            }
    }
}
