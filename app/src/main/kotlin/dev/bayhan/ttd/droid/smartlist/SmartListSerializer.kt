package dev.bayhan.ttd.droid.smartlist

object SmartListSerializer {

    fun serialize(list: SmartList): String = buildString {
        appendLine("---")
        appendLine("name: ${list.name}")
        if (list.icon != null) appendLine("icon: ${list.icon}")
        if (list.description != null) appendLine("description: ${list.description}")
        if (list.order != 0) appendLine("order: ${list.order}")
        appendLine("---")

        if (list.conditions.isNotEmpty()) {
            for ((i, block) in list.conditions.withIndex()) {
                if (i > 0) appendLine("OR")
                for (condition in block.conditions) {
                    appendLine(serializeCondition(condition))
                }
            }
            appendLine()
        }

        for (sort in list.sorts) {
            val dir = if (sort.ascending) " asc" else " desc"
            appendLine("sort by ${sort.field}$dir")
        }

        for (group in list.groups) {
            val dir = if (group.ascending) " asc" else " desc"
            appendLine("group by ${group.field}$dir")
        }

        for (prefill in list.prefills) {
            appendLine("prefill ${prefill.field} ${prefill.value}")
        }
    }

    private fun serializeCondition(condition: Condition): String = when (condition) {
        is DoneCondition -> if (condition.done) "done" else "not done"
        is PriorityCondition -> {
            val op = when (condition.op) {
                PriorityOp.ABOVE -> "above"
                PriorityOp.BELOW -> "below"
                PriorityOp.EQ -> "="
            }
            "priority $op ${condition.value}"
        }
        is TextCondition -> {
            val field = textFieldName(condition.field)
            val op = when (condition.op) {
                TextOp.INCLUDES -> "includes"
                TextOp.EXCLUDES -> "excludes"
                TextOp.EQUALS -> "equals"
            }
            "$field $op ${condition.value}"
        }
        is ExistsCondition -> {
            val prefix = if (condition.has) "has" else "no"
            val field = fieldName(condition.field)
            "$prefix $field"
        }
        is TimeExistsCondition -> {
            val prefix = if (condition.has) "has time" else "no time"
            "$prefix ${dateFieldName(condition.field)}"
        }
        is DateCondition -> {
            val field = dateFieldName(condition.field)
            val op = when (condition.op) {
                CompareOp.LT -> "<"
                CompareOp.LTE -> "<="
                CompareOp.GT -> ">"
                CompareOp.GTE -> ">="
                CompareOp.EQ -> "="
                CompareOp.NEQ -> "!="
            }
            val value = serializeDateValue(condition.value)
            "$field $op $value"
        }
    }

    private fun serializeDateValue(dv: DateValue): String {
        val base = when (dv.anchor) {
            DateAnchor.TODAY -> "today"
            DateAnchor.ABSOLUTE -> dv.anchorDate.orEmpty()
        }
        val offset = when {
            dv.offset > 0 -> "+${dv.offset}"
            dv.offset < 0 -> dv.offset.toString()
            else -> ""
        }
        val time = dv.anchorTime?.let { "T$it" }.orEmpty()
        return base + offset + time
    }

    private fun textFieldName(field: TextField): String = when (field) {
        TextField.PROJECT -> "project"
        TextField.CONTEXT -> "context"
        TextField.DESCRIPTION -> "description"
    }

    private fun fieldName(field: Field): String = when (field) {
        Field.DUE -> "due"
        Field.SCHEDULED -> "scheduled"
        Field.STARTING -> "starting"
        Field.UPDATED -> "updated"
        Field.CREATION_DATE -> "creation_date"
        Field.PRIORITY -> "priority"
        Field.PROJECT -> "project"
        Field.CONTEXT -> "context"
        Field.DESCRIPTION -> "description"
    }

    private fun dateFieldName(field: DateField): String = when (field) {
        DateField.DUE -> "due"
        DateField.SCHEDULED -> "scheduled"
        DateField.STARTING -> "starting"
        DateField.UPDATED -> "updated"
        DateField.CREATION_DATE -> "creation_date"
    }
}
