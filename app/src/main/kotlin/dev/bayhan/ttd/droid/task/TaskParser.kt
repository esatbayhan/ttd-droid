package dev.bayhan.ttd.droid.task

object TaskParser {

    private val priorityPattern = Regex("^\\([A-Z]\\)$")
    private val projectPattern = Regex("(?:^|\\s)\\+(\\S+)")
    private val contextPattern = Regex("(?:^|\\s)@(\\S+)")
    private val tagPattern = Regex("(?:^|\\s)([^\\s:]+):(\\S+)(?=\\s|$)")

    private val dateValueTagKeys = setOf("due", "scheduled", "starting", "updated")

    private fun leadingDate(text: String): Pair<String, String>? {
        val token = text.substringBefore(' ')
        if (TaskDateTime.parse(token) == null) return null
        return token to text.removePrefix(token).trimStart()
    }

    fun parse(line: String): Task {
        if (line.isBlank()) {
            return Task(
                done = false, completionDate = null, priority = null,
                creationDate = null, description = "",
                projects = emptyList(), contexts = emptyList(),
                tags = emptyMap(), raw = line
            )
        }

        var rest = line.trim()
        var done = false
        var completionDate: String? = null
        var priority: Char? = null
        var creationDate: String? = null

        if (rest.startsWith("x ")) {
            leadingDate(rest.substring(2))?.let { (date, remaining) ->
                done = true
                completionDate = date
                rest = remaining
            }
        }

        if (!done && rest.length >= 4 && rest[0] == '(' && rest[3] == ' ') {
            val candidate = rest.substring(0, 3)
            if (priorityPattern.matches(candidate)) {
                priority = candidate[1]
                rest = rest.substring(4)
            }
        }

        leadingDate(rest)?.let { (date, remaining) ->
            creationDate = date
            rest = remaining
        }

        val description = rest

        val projects = projectPattern.findAll(description).map { it.groupValues[1] }.toList()
        val contexts = contextPattern.findAll(description).map { it.groupValues[1] }.toList()

        val rawTags = tagPattern.findAll(description)
        val tags = mutableMapOf<String, String>()
        val consumedKeys = mutableSetOf<String>()
        for (match in rawTags) {
            val key = match.groupValues[1]
            val value = match.groupValues[2]

            if (key in consumedKeys) continue
            consumedKeys.add(key)

            if (key in dateValueTagKeys && TaskDateTime.parse(value) == null) continue

            tags[key] = value
        }

        return Task(
            done = done,
            completionDate = completionDate,
            priority = priority,
            creationDate = creationDate,
            description = description,
            projects = projects,
            contexts = contexts,
            tags = tags.toMap(),
            raw = line
        )
    }

    fun format(task: Task): String {
        val sb = StringBuilder()

        if (task.done) {
            sb.append("x ")
            task.completionDate?.let { sb.append(it).append(' ') }
        } else {
            task.priority?.let { sb.append("($it) ") }
        }

        task.creationDate?.let { sb.append(it).append(' ') }
        sb.append(task.description)

        return sb.toString()
    }
}
