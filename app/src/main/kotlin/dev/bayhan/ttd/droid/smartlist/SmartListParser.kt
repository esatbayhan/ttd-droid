package dev.bayhan.ttd.droid.smartlist

import dev.bayhan.ttd.droid.task.TaskDateTime
import java.time.LocalDate

object SmartListParser {
    private val templateRegex = Regex("""\{\{dir(?::(\d+))?\}\}""")
    private val filterTemplateValueRegex = Regex(
        """^(?:(?:due|scheduled|starting|updated|creation_date) (?:<=|>=|==|!=|=|<|>)|priority (?:above|below|=)|(?:project|context|description) (?:includes|excludes|equals)) .+$"""
    )
    private val dateValueRegex = Regex(
        "^(today|\\d{4}-\\d{2}-\\d{2})(?:\\s*([+-])\\s*(\\d+))?\\s*(?:T(\\d{2}:\\d{2}))?$"
    )
    private val dateFields = mapOf(
        "due" to DateField.DUE,
        "scheduled" to DateField.SCHEDULED,
        "starting" to DateField.STARTING,
        "updated" to DateField.UPDATED,
        "creation_date" to DateField.CREATION_DATE
    )

    fun parse(input: String, groupPath: String): SmartList? {
        val resolved = resolveTemplates(input, groupPath) ?: return null
        return parse(resolved)
    }

    fun parse(input: String): SmartList {
        val lines = input.split('\n').map { it.removeSuffix("\r") }
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
        val scalarPrefills = mutableSetOf<String>()
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
                        val rest = line.trimStart().removePrefix("prefill ")
                        val separator = rest.indexOf(' ')
                        if (separator > 0) {
                            val field = rest.substring(0, separator)
                            val value = rest.substring(separator + 1)
                            val valid = when (field) {
                                "project" -> isValidTextValue(value) && '+' !in value
                                "context" -> isValidTextValue(value) && '@' !in value
                                "priority" -> value.length == 1 && value[0] in 'A'..'Z'
                                "due", "scheduled", "starting" -> parseDateValue(value) != null
                                else -> false
                            }
                            val isScalar = field != "project" && field != "context"
                            if (valid && (!isScalar || scalarPrefills.add(field))) {
                                prefills.add(Prefill(field, value))
                            }
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

    private fun resolveTemplates(input: String, groupPath: String): String? {
        val groups = groupPath.split('/').filter { it.isNotEmpty() }
        var delimiterCount = 0
        var invalid = false
        val resolved = input.split('\n').map { it.removeSuffix("\r") }.map { line ->
            val trimmed = line.trim()
            if (trimmed == "---") {
                delimiterCount++
                line
            } else if (delimiterCount < 2 || !isTemplateValuePosition(trimmed)) {
                line
            } else {
                templateRegex.replace(line) { match ->
                    val depthText = match.groups[1]?.value
                    val depth = depthText?.toIntOrNull() ?: if (depthText == null) 0 else null
                    val index = depth?.let { groups.lastIndex - it }
                    if (index == null || index < 0) {
                        invalid = true
                        match.value
                    } else {
                        groups[index]
                    }
                }
            }
        }.joinToString("\n")
        return resolved.takeUnless { invalid }
    }

    private fun isTemplateValuePosition(line: String): Boolean =
        line.matches(filterTemplateValueRegex) ||
            line.startsWith("prefill project ") ||
            line.startsWith("prefill context ")

    private fun isValidTextValue(value: String): Boolean =
        value.isNotEmpty() &&
            !value.first().isWhitespace() &&
            !value.last().isWhitespace() &&
            value.none { it != ' ' && it.isWhitespace() }

    private fun parseCondition(text: String): Condition? {
        when {
            text == "done" -> return DoneCondition(true)
            text == "not done" -> return DoneCondition(false)
            text.startsWith("has time ") -> {
                val field = dateFields[text.removePrefix("has time ").trim()]
                return field?.let { TimeExistsCondition(true, it) }
            }
            text.startsWith("no time ") -> {
                val field = dateFields[text.removePrefix("no time ").trim()]
                return field?.let { TimeExistsCondition(false, it) }
            }
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
        val match = dateValueRegex.matchEntire(text.trim()) ?: return null
        val anchorText = match.groupValues[1]
        if (anchorText != "today" && TaskDateTime.parse(anchorText) == null) return null
        val time = match.groupValues[4].takeIf { it.isNotEmpty() }
        if (time != null && TaskDateTime.parse("2000-01-01T$time") == null) return null
        val magnitudeText = match.groupValues[3]
        val magnitude = if (magnitudeText.isEmpty()) 0 else magnitudeText.toIntOrNull() ?: return null
        val offset = if (match.groupValues[2] == "-") -magnitude else magnitude
        return if (anchorText == "today") {
            DateValue(DateAnchor.TODAY, offset, anchorTime = time)
        } else {
            DateValue(DateAnchor.ABSOLUTE, offset, anchorText, time)
        }
    }

    fun resolveDateValue(text: String, today: LocalDate = LocalDate.now()): String? {
        val value = parseDateValue(text) ?: return null
        val base = when (value.anchor) {
            DateAnchor.TODAY -> today
            DateAnchor.ABSOLUTE -> value.anchorDate?.let(LocalDate::parse) ?: return null
        }.plusDays(value.offset.toLong())
        return base.toString() + value.anchorTime?.let { "T$it" }.orEmpty()
    }
}
