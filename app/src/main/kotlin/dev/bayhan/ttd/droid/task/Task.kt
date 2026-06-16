package dev.bayhan.ttd.droid.task

data class Task(
    val done: Boolean,
    val completionDate: String?,
    val priority: Char?,
    val creationDate: String?,
    val description: String,
    val projects: List<String>,
    val contexts: List<String>,
    val tags: Map<String, String>,
    val raw: String,
    val filename: String = ""
)
