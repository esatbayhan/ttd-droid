package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bayhan.ttd.droid.R

@Composable
fun PriorityBadge(priority: Char?, modifier: Modifier = Modifier) {
    if (priority == null) return
    val color = when (priority) {
        'A' -> MaterialTheme.colorScheme.error
        'B' -> MaterialTheme.colorScheme.primary
        'C' -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val badgeDesc = stringResource(R.string.priority_badge_cd, priority)
    Box(
        modifier = modifier.size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = priority.toString(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.semantics {
                contentDescription = badgeDesc
            }
        )
    }
}
