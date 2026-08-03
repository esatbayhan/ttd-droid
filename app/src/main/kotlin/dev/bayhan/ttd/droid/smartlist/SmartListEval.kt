package dev.bayhan.ttd.droid.smartlist

import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.task.TaskDateTime
import java.time.LocalDate
import java.time.LocalTime

object SmartListEval {

    fun matches(task: Task, list: SmartList): Boolean {
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
            is TimeExistsCondition -> evaluateTimeExists(task, condition)
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

    private fun dateValue(task: Task, field: DateField): String? = when (field) {
        DateField.DUE -> task.tags["due"]
        DateField.SCHEDULED -> task.tags["scheduled"]
        DateField.STARTING -> task.tags["starting"]
        DateField.UPDATED -> task.tags["updated"]
        DateField.CREATION_DATE -> task.creationDate
    }

    private fun evaluateTimeExists(task: Task, cond: TimeExistsCondition): Boolean {
        val parsed = dateValue(task, cond.field)?.let(TaskDateTime::parse) ?: return false
        return (parsed.time != null) == cond.has
    }

    private fun evaluateDate(task: Task, cond: DateCondition): Boolean {
        val taskValue = dateValue(task, cond.field)?.let(TaskDateTime::parse) ?: return false
        val baseDate = when (cond.value.anchor) {
            DateAnchor.TODAY -> LocalDate.now()
            DateAnchor.ABSOLUTE -> cond.value.anchorDate?.let(LocalDate::parse) ?: return false
        }.plusDays(cond.value.offset.toLong())

        val comparison = if (cond.value.anchorTime == null) {
            taskValue.date.compareTo(baseDate)
        } else {
            val taskDateTime = taskValue.date.atTime(taskValue.time ?: LocalTime.MIDNIGHT)
            val anchorDateTime = baseDate.atTime(LocalTime.parse(cond.value.anchorTime))
            taskDateTime.compareTo(anchorDateTime)
        }

        return when (cond.op) {
            CompareOp.LT -> comparison < 0
            CompareOp.LTE -> comparison <= 0
            CompareOp.GT -> comparison > 0
            CompareOp.GTE -> comparison >= 0
            CompareOp.EQ -> comparison == 0
            CompareOp.NEQ -> comparison != 0
        }
    }

    private fun sortableDate(raw: String?): String {
        val value = raw?.let(TaskDateTime::parse) ?: return ""
        return value.date.atTime(value.time ?: LocalTime.MIDNIGHT).toString()
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
            "creation_date" -> compareBy { sortableDate(it.creationDate) }
            "due" -> compareBy { sortableDate(it.tags["due"]) }
            "scheduled" -> compareBy { sortableDate(it.tags["scheduled"]) }
            "starting" -> compareBy { sortableDate(it.tags["starting"]) }
            "updated" -> compareBy { sortableDate(it.tags["updated"]) }
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
                "due" -> task.tags["due"]?.let(TaskDateTime::datePart) ?: "No Due Date"
                "scheduled" -> task.tags["scheduled"]?.let(TaskDateTime::datePart) ?: "Not Scheduled"
                "starting" -> task.tags["starting"]?.let(TaskDateTime::datePart) ?: "Not Starting"
                "updated" -> task.tags["updated"]?.let(TaskDateTime::datePart) ?: "Not Updated"
                "creation_date" -> task.creationDate?.let(TaskDateTime::datePart) ?: "No Creation Date"
                "description" -> task.description.firstOrNull()?.toString() ?: ""
                else -> ""
            }
        }
        val ordered = when (dir.field) {
            "due", "scheduled", "starting", "updated", "creation_date" -> grouped.mapValues { (_, values) ->
                values.sortedWith(fieldComparator(dir.field, dir.ascending))
            }
            else -> grouped
        }

        return if (dir.ascending) {
            ordered.toSortedMap(compareBy { it })
        } else {
            ordered.toSortedMap(compareByDescending { it })
        }
    }
}
