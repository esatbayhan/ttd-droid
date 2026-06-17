package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun PriorityCheckbox(
    isDone: Boolean,
    priority: Char?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayChar = when {
        isDone -> "✓"
        priority != null -> priority.toString()
        else -> ""
    }
    val activeColor = if (isDone) {
        MaterialTheme.colorScheme.primary
    } else {
        when (priority) {
            'A' -> MaterialTheme.colorScheme.error
            'B' -> MaterialTheme.colorScheme.primary
            'C' -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    }
    val borderColor = when {
        isDone || priority != null -> activeColor
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(2.dp, borderColor, RoundedCornerShape(4.dp))
                .then(
                    if (isDone) Modifier.background(activeColor.copy(alpha = 0.12f))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayChar,
                color = activeColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
