package dev.bayhan.ttd.droid.smartlist

object SmartListParser {
    private val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val todayOffsetRegex = Regex("^today\\s*([+-])\\s*(\\d+)$")
    private val absDateOffsetRegex = Regex("^(\\d{4}-\\d{2}-\\d{2})\\s*([+-])\\s*(\\d+)$")

    fun parse(input: String): SmartList {
        val lines = input.lines()
        var inFrontmatter = false
        var inBody = false
        var name = ""
        var icon: String? = null
        var description: String? = null
        var order = 0
        val conditions = mutableListOf<FilterBlock>()
        val sorts = mutableListOf<Directive>()
        val groups = mutableListOf<Directive>()
        val prefills = mutableListOf<Prefill>()
        var currentBlock = mutableListOf<Condition>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "---") {
                if (!inFrontmatter) {
                    inFrontmatter = true
                    continue
                } else {
                    inFrontmatter = false
                    inBody = true
                    continue
                }
            }

            if (inFrontmatter) {
                val parts = trimmed.split(":", limit = 2)
                if (parts.size == 2) {
                    val value = parts[1].trim()
                    when (parts[0].trim()) {
                        "name" -> name = value
                        "icon" -> icon = value
                        "description" -> description = value
                        "order" -> order = value.toIntOrNull() ?: 0
                    }
                }
                continue
            }

            if (inBody && trimmed.isNotEmpty()) {
                when {
                    trimmed.equals("OR", ignoreCase = true) -> {
                        if (currentBlock.isNotEmpty()) {
                            conditions.add(FilterBlock(currentBlock.toList()))
                            currentBlock = mutableListOf()
                        }
                    }
                    trimmed.startsWith("sort by") -> {
                        val rest = trimmed.removePrefix("sort by").trim()
                        val parts = rest.split(" ")
                        val field = parts[0]
                        val ascending = parts.getOrNull(1)?.lowercase() != "desc"
                        sorts.add(Directive(field, ascending))
                    }
                    trimmed.startsWith("group by") -> {
                        val rest = trimmed.removePrefix("group by").trim()
                        val parts = rest.split(" ")
                        val field = parts[0]
                        val ascending = parts.getOrNull(1)?.lowercase() != "desc"
                        groups.add(Directive(field, ascending))
                    }
                    trimmed.startsWith("prefill ") -> {
                        val rest = trimmed.removePrefix("prefill ").trim()
                        val parts = rest.split(" ", limit = 2)
                        if (parts.size == 2) {
                            prefills.add(Prefill(parts[0], parts[1]))
                        }
                    }
                    else -> {
                        val condition = parseCondition(trimmed)
                        if (condition != null) {
                            currentBlock.add(condition)
                        }
                    }
                }
            }
        }

        if (currentBlock.isNotEmpty()) {
            conditions.add(FilterBlock(currentBlock.toList()))
        }

        return SmartList(
            name = name,
            icon = icon,
            description = description,
            order = order,
            conditions = conditions,
            sorts = sorts,
            groups = groups,
            prefills = prefills
        )
    }

    private fun parseCondition(text: String): Condition? {
        when {
            text == "done" -> return DoneCondition(true)
            text == "not done" -> return DoneCondition(false)
            text.startsWith("has ") -> {
                val field = parseField(text.removePrefix("has "))
                return field?.let { ExistsCondition(true, it) }
            }
            text.startsWith("no ") -> {
                val field = parseField(text.removePrefix("no "))
                return field?.let { ExistsCondition(false, it) }
            }
            text.startsWith("priority ") -> {
                val rest = text.removePrefix("priority ")
                if (rest.startsWith("above ")) {
                    val v = rest.removePrefix("above ").trim()
                    if (v.length == 1 && v[0] in 'A'..'Z')
                        return PriorityCondition(PriorityOp.ABOVE, v[0])
                }
                if (rest.startsWith("below ")) {
                    val v = rest.removePrefix("below ").trim()
                    if (v.length == 1 && v[0] in 'A'..'Z')
                        return PriorityCondition(PriorityOp.BELOW, v[0])
                }
                if (rest.startsWith("= ")) {
                    val v = rest.removePrefix("= ").trim()
                    if (v.length == 1 && v[0] in 'A'..'Z')
                        return PriorityCondition(PriorityOp.EQ, v[0])
                }
            }
            text.startsWith("project ") -> {
                val rest = text.removePrefix("project ")
                return parseTextCondition(TextField.PROJECT, rest)
            }
            text.startsWith("context ") -> {
                val rest = text.removePrefix("context ")
                return parseTextCondition(TextField.CONTEXT, rest)
            }
            text.startsWith("description ") -> {
                val rest = text.removePrefix("description ")
                return parseTextCondition(TextField.DESCRIPTION, rest)
            }
            text.startsWith("due ") -> {
                val rest = text.removePrefix("due ")
                return parseDateCondition(DateField.DUE, rest)
            }
            text.startsWith("scheduled ") -> {
                val rest = text.removePrefix("scheduled ")
                return parseDateCondition(DateField.SCHEDULED, rest)
            }
            text.startsWith("starting ") -> {
                val rest = text.removePrefix("starting ")
                return parseDateCondition(DateField.STARTING, rest)
            }
            text.startsWith("updated ") -> {
                val rest = text.removePrefix("updated ")
                return parseDateCondition(DateField.UPDATED, rest)
            }
            text.startsWith("creation_date ") -> {
                val rest = text.removePrefix("creation_date ")
                return parseDateCondition(DateField.CREATION_DATE, rest)
            }
        }
        return null
    }

    private fun parseField(text: String): Field? {
        return when (text.trim()) {
            "due" -> Field.DUE
            "scheduled" -> Field.SCHEDULED
            "starting" -> Field.STARTING
            "updated" -> Field.UPDATED
            "creation_date" -> Field.CREATION_DATE
            "priority" -> Field.PRIORITY
            "project" -> Field.PROJECT
            "context" -> Field.CONTEXT
            "description" -> Field.DESCRIPTION
            else -> null
        }
    }

    private fun parseTextCondition(field: TextField, rest: String): Condition? {
        return when {
            rest.startsWith("includes ") ->
                TextCondition(field, TextOp.INCLUDES, rest.removePrefix("includes "))
            rest.startsWith("excludes ") ->
                TextCondition(field, TextOp.EXCLUDES, rest.removePrefix("excludes "))
            rest.startsWith("equals ") ->
                TextCondition(field, TextOp.EQUALS, rest.removePrefix("equals "))
            else -> null
        }
    }

    private fun parseDateCondition(field: DateField, rest: String): Condition? {
        val parts = rest.split(" ", limit = 3)
        if (parts.size < 2) return null
        val op = when (parts[0]) {
            "<=" -> CompareOp.LTE
            ">=" -> CompareOp.GTE
            "<" -> CompareOp.LT
            ">" -> CompareOp.GT
            "==", "=" -> CompareOp.EQ
            "!=" -> CompareOp.NEQ
            else -> return null
        }
        val valueStr = parts.drop(1).joinToString(" ")
        val value = parseDateValue(valueStr) ?: return null
        return DateCondition(field, op, value)
    }

    private fun parseDateValue(text: String): DateValue? {
        val t = text.trim()
        if (t == "today") return DateValue(DateAnchor.TODAY, 0)
        val todayOffset = todayOffsetRegex.find(t)
        if (todayOffset != null) {
            val sign = if (todayOffset.groupValues[1] == "+") 1 else -1
            val num = todayOffset.groupValues[2].toIntOrNull() ?: return null
            return DateValue(DateAnchor.TODAY, sign * num)
        }
        val absDateOffset = absDateOffsetRegex.find(t)
        if (absDateOffset != null) {
            val base = absDateOffset.groupValues[1]
            val sign = if (absDateOffset.groupValues[2] == "+") 1 else -1
            val num = absDateOffset.groupValues[3].toIntOrNull() ?: return null
            return DateValue(DateAnchor.ABSOLUTE, sign * num, base)
        }
        if (dateRegex.matches(t)) {
            return DateValue(DateAnchor.ABSOLUTE, 0, t)
        }
        return null
    }
}
