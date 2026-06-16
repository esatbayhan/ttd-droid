package dev.bayhan.ttd.droid.store

import androidx.documentfile.provider.DocumentFile

data class SnapshotIndex(
    val entries: Map<String, Long>
) {
    fun diff(other: SnapshotIndex): RefreshDelta {
        val created = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        for ((name, mtime) in other.entries) {
            val prev = entries[name]
            if (prev == null) created.add(name)
            else if (prev != mtime) updated.add(name)
        }
        for (name in entries.keys) {
            if (name !in other.entries) deleted.add(name)
        }

        return RefreshDelta(created, updated, deleted)
    }

    companion object {
        fun build(rootDoc: DocumentFile): SnapshotIndex {
            val entries = mutableMapOf<String, Long>()
            collectEntries(rootDoc, entries)
            return SnapshotIndex(entries)
        }

        private fun collectEntries(dir: DocumentFile, entries: MutableMap<String, Long>) {
            val files = try {
                dir.listFiles()
            } catch (_: Exception) { return }

            files.forEach { file ->
                if (file.isDirectory) {
                    collectEntries(file, entries)
                } else if (file.name?.endsWith(".txt") == true) {
                    val name = file.name!!
                    try {
                        entries[name] = file.lastModified()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}

data class RefreshDelta(
    val created: List<String>,
    val updated: List<String>,
    val deleted: List<String>
)
