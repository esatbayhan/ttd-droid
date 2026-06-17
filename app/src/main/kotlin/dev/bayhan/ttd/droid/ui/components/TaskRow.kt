package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.bayhan.ttd.droid.task.Task
import dev.bayhan.ttd.droid.ui.stripDateTokens
import dev.bayhan.ttd.droid.ui.stripUpdatedToken

private val highlightPattern = Regex("""\+\S+|@\S+|(?:due|scheduled|starting):\d{4}-\d{2}-\d{2}""")

@Composable
fun TaskRow(
    task: Task,
    isDone: Boolean = task.done,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    hideDateValues: Boolean = false,
    hideUpdatedDate: Boolean = true,
    highlighted: Boolean = false
) {
    val dueColor = MaterialTheme.colorScheme.error
    val scheduledColor = MaterialTheme.colorScheme.primary
    val projectColor = MaterialTheme.colorScheme.secondary
    val contextColor = MaterialTheme.colorScheme.tertiary
    val tagColor = MaterialTheme.colorScheme.outline

    val textColor = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
    val textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None

    val description = {
        var d = task.description
        if (hideDateValues) d = stripDateTokens(d)
        if (hideUpdatedDate) d = stripUpdatedToken(d)
        d
    }()
    val annotatedDescription = remember(description) {
        buildDescription(description, dueColor, scheduledColor, projectColor, contextColor)
    }

    Row(
        modifier = modifier
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
            .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PriorityCheckbox(
            isDone = isDone,
            priority = task.priority,
            onToggle = { onToggleDone() }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick, role = Role.Button)
        ) {
            Text(
                text = annotatedDescription,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                textDecoration = textDecoration
            )
            val hasTags = task.tags.isNotEmpty() || task.projects.isNotEmpty() || task.contexts.isNotEmpty()
            if (hasTags) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.tags.forEach { (key, value) ->
                        val color = when (key) {
                            "due" -> dueColor
                            "scheduled", "starting" -> scheduledColor
                            else -> tagColor
                        }
                        Text("$key:$value", color = color, style = MaterialTheme.typography.labelSmall)
                    }
                    task.projects.forEach {
                        Text("+$it", color = projectColor, style = MaterialTheme.typography.labelSmall)
                    }
                    task.contexts.forEach {
                        Text("@$it", color = contextColor, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun buildDescription(
    description: String,
    dueColor: Color,
    scheduledColor: Color,
    projectColor: Color,
    contextColor: Color
) = buildAnnotatedString {
    var lastIndex = 0
    for (match in highlightPattern.findAll(description)) {
        val start = match.range.first
        val end = match.range.last + 1
        if (start > lastIndex) {
            append(description.substring(lastIndex, start))
        }
        val token = match.value
        val color = when {
            token.startsWith("due:") -> dueColor
            token.startsWith("scheduled:") || token.startsWith("starting:") -> scheduledColor
            token.startsWith("+") -> projectColor
            token.startsWith("@") -> contextColor
            else -> Color.Unspecified
        }
        withStyle(SpanStyle(color = color)) {
            append(token)
        }
        lastIndex = end
    }
    if (lastIndex < description.length) {
        append(description.substring(lastIndex))
    }
}
