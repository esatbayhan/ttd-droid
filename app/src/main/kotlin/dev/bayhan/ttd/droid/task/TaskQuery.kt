package dev.bayhan.ttd.droid.task

object TaskQuery {

    fun defaultSort(tasks: List<Task>): List<Task> {
        return tasks.sortedWith(
            compareBy<Task> { it.done }
                .thenByDescending { it.priority != null }
                .thenBy { it.priority ?: '\u0000' }
                .thenBy { it.creationDate ?: "" }
                .thenBy { it.description }
        )
    }
}
