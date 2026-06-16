package dev.bayhan.ttd.droid.smartlist

import dev.bayhan.ttd.droid.task.Task
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object SmartListEval {

    fun matches(task: Task, list: SmartList): Boolean {
        if (list.conditions.isEmpty()) return true
        return list.conditions.any { block ->
            block.conditions.all { condition -> evaluateCondition(task, condition) }
        }
    }

    private fun evaluateCondition(task: Task, condition: Condition): Boolean {
        return when (condition) {
            is DoneCondition -> task.done == condition.done
            is PriorityCondition -> evaluatePriority(task, condition)
            is TextCondition -> evaluateText(task, condition)
            is ExistsCondition -> evaluateExists(task, condition)
            is DateCondition -> evaluateDate(task, condition)
        }
    }

    private fun evaluatePriority(task: Task, cond: PriorityCondition): Boolean {
        val p = task.priority ?: return false
        return when (cond.op) {
            PriorityOp.ABOVE -> p < cond.value
            PriorityOp.BELOW -> p > cond.value
            PriorityOp.EQ -> p == cond.value
        }
    }

    private fun evaluateText(task: Task, cond: TextCondition): Boolean {
        val values = when (cond.field) {
            TextField.PROJECT -> task.projects
            TextField.CONTEXT -> task.contexts
            TextField.DESCRIPTION -> task.description
        }
        val valueStr = when (values) {
            is List<*> -> values.joinToString(" ")
            is String -> values
            else -> values.toString()
        }
        return when (cond.op) {
            TextOp.INCLUDES -> valueStr.contains(cond.value, ignoreCase = true)
            TextOp.EXCLUDES -> !valueStr.contains(cond.value, ignoreCase = true)
            TextOp.EQUALS -> valueStr.equals(cond.value, ignoreCase = true)
        }
    }

    private fun evaluateExists(task: Task, cond: ExistsCondition): Boolean {
        val exists = when (cond.field) {
            Field.DUE -> task.tags.containsKey("due")
            Field.SCHEDULED -> task.tags.containsKey("scheduled")
            Field.STARTING -> task.tags.containsKey("starting")
            Field.UPDATED -> task.tags.containsKey("updated")
            Field.CREATION_DATE -> task.creationDate != null
            Field.PRIORITY -> task.priority != null
            Field.PROJECT -> task.projects.isNotEmpty()
            Field.CONTEXT -> task.contexts.isNotEmpty()
            Field.DESCRIPTION -> task.description.isNotBlank()
        }
        return if (cond.has) exists else !exists
    }

    private fun evaluateDate(task: Task, cond: DateCondition): Boolean {
        val dateStr = when (cond.field) {
            DateField.DUE -> task.tags["due"]
            DateField.SCHEDULED -> task.tags["scheduled"]
            DateField.STARTING -> task.tags["starting"]
            DateField.UPDATED -> task.tags["updated"]
            DateField.CREATION_DATE -> task.creationDate
        } ?: return false

        val taskDate = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            return false
        }
        val today = LocalDate.now()

        val threshold = when (cond.value.anchor) {
            DateAnchor.TODAY -> today.plusDays(cond.value.offset.toLong())
            DateAnchor.ABSOLUTE -> {
                val baseDate = cond.value.anchorDate?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (_: Exception) {
                        null
                    }
                } ?: today
                baseDate.plusDays(cond.value.offset.toLong())
            }
        }

        val diff = ChronoUnit.DAYS.between(threshold, taskDate)

        return when (cond.op) {
            CompareOp.LT -> diff < 0
            CompareOp.LTE -> diff <= 0
            CompareOp.GT -> diff > 0
            CompareOp.GTE -> diff >= 0
            CompareOp.EQ -> diff == 0L
            CompareOp.NEQ -> diff != 0L
        }
    }

    fun sort(tasks: List<Task>, directives: List<Directive>): List<Task> {
        if (directives.isEmpty()) return tasks
        var comparator: Comparator<Task>? = null
        for (dir in directives) {
            val fieldComparator = fieldComparator(dir.field, dir.ascending)
            comparator = if (comparator == null) fieldComparator else comparator.thenComparing(fieldComparator)
        }
        return tasks.sortedWith(comparator ?: compareBy { 0 })
    }

    private fun fieldComparator(field: String, ascending: Boolean): Comparator<Task> {
        val comp: Comparator<Task> = when (field) {
            "priority" -> compareBy { it.priority ?: 'Z' + 1 }
            "done" -> compareBy { it.done }
            "creation_date" -> compareBy { it.creationDate ?: "" }
            "due" -> compareBy { it.tags["due"] ?: "" }
            "scheduled" -> compareBy { it.tags["scheduled"] ?: "" }
            "starting" -> compareBy { it.tags["starting"] ?: "" }
            "updated" -> compareBy { it.tags["updated"] ?: "" }
            "description" -> compareBy { it.description }
            "project" -> compareBy { it.projects.firstOrNull() ?: "" }
            "context" -> compareBy { it.contexts.firstOrNull() ?: "" }
            else -> compareBy { 0 }
        }
        return if (ascending) comp else comp.reversed()
    }

    fun group(tasks: List<Task>, directives: List<Directive>): Map<String, List<Task>> {
        if (directives.isEmpty()) return mapOf("" to tasks)

        val dir = directives.first()
        val grouped = tasks.groupBy { task ->
            when (dir.field) {
                "project" -> task.projects.firstOrNull() ?: "No Project"
                "context" -> task.contexts.firstOrNull() ?: "No Context"
                "priority" -> task.priority?.toString() ?: "No Priority"
                "done" -> if (task.done) "Done" else "Open"
                "due" -> task.tags["due"] ?: "No Due Date"
                "scheduled" -> task.tags["scheduled"] ?: "Not Scheduled"
                "starting" -> task.tags["starting"] ?: "Not Starting"
                "updated" -> task.tags["updated"] ?: "Not Updated"
                "creation_date" -> task.creationDate ?: "No Creation Date"
                "description" -> task.description.firstOrNull()?.toString() ?: ""
                else -> ""
            }
        }

        return if (dir.ascending) {
            grouped.toSortedMap(compareBy { it })
        } else {
            grouped.toSortedMap(compareByDescending { it })
        }
    }
}
