package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.bayhan.ttd.droid.R

@Composable
fun SmartListSourceView(raw: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val annotatedText = remember(raw, colors) {
        buildListHighlightedText(
            raw = raw,
            primary = colors.primary,
            secondary = colors.secondary,
            tertiary = colors.tertiary,
            error = colors.error,
            outlineVariant = colors.outlineVariant
        )
    }

    val sourceCd = stringResource(R.string.action_show_list_source)
    if (raw.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.smartlist_source_empty))
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
        ) {
            SelectionContainer {
                Text(
                    text = annotatedText,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .semantics { contentDescription = sourceCd }
                        .padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun buildListHighlightedText(
    raw: String,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    error: Color,
    outlineVariant: Color
) = buildListHighlightedText(raw, ListSyntaxColors(
    delim = outlineVariant,
    comment = outlineVariant,
    keyword = primary,
    operator = error,
    date = secondary,
    directive = tertiary,
    orKeyword = error,
    template = secondary
))
