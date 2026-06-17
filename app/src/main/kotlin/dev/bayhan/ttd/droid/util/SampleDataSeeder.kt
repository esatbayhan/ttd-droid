package dev.bayhan.ttd.droid.util

import android.content.Context
import androidx.documentfile.provider.DocumentFile

object SampleDataSeeder {

    fun seed(context: Context, targetDir: DocumentFile): Boolean {
        return try {
            copyAssets(context, "sample", targetDir)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun copyAssets(context: Context, path: String, targetDir: DocumentFile) {
        val assetManager = context.assets
        val entries = assetManager.list(path) ?: return

        for (entry in entries) {
            val childPath = if (path.isEmpty()) entry else "$path/$entry"
            val subEntries = assetManager.list(childPath)
            val isDirectory = subEntries != null && subEntries.isNotEmpty()

            if (isDirectory) {
                val subDir = targetDir.createDirectory(entry) ?: targetDir.findFile(entry)
                    ?: continue
                copyAssets(context, childPath, subDir)
            } else {
                val content = assetManager.open(childPath).bufferedReader().use { it.readText() }
                val resolved = if (entry.endsWith(".txt")) {
                    DateResolver.resolve(content)
                } else {
                    content
                }
                val file = targetDir.createFile("text/plain", entry) ?: continue
                context.contentResolver.openOutputStream(file.uri)?.use { stream ->
                    stream.write(resolved.toByteArray())
                }
            }
        }
    }
}
