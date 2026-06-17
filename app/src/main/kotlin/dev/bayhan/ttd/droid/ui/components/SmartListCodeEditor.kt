package dev.bayhan.ttd.droid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SmartListCodeEditor(
    raw: String,
    onRawChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    validationBanner: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val textState = remember { mutableStateOf(TextFieldValue(raw)) }

    LaunchedEffect(raw) {
        if (textState.value.text != raw) {
            textState.value = TextFieldValue(raw)
        }
    }

    val syntaxColors = remember(MaterialTheme.colorScheme) {
        ListSyntaxColors(
            delim = colors.outlineVariant,
            comment = colors.outlineVariant,
            keyword = colors.primary,
            operator = colors.error,
            date = colors.secondary,
            directive = colors.tertiary,
            orKeyword = colors.error,
            template = colors.secondary
        )
    }

    val textFieldValue = remember(textState.value.text, syntaxColors) {
        val annotated = buildListHighlightedText(textState.value.text, syntaxColors)
        TextFieldValue(
            annotatedString = annotated,
            selection = textState.value.selection
        )
    }

    val (isValid, errorMsg) = remember(textState.value.text) {
        parseValidity(textState.value.text)
    }

    LaunchedEffect(textState.value.text) {
        if (textState.value.text != raw) {
            onRawChange(textState.value.text)
        }
    }

    Column(modifier = modifier) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { textState.value = it },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = colors.onSurface
            ),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        if (validationBanner) {
            Spacer(modifier = Modifier.height(4.dp))
            if (isValid) {
                Text(
                    "✓ Valid syntax",
                    color = colors.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    "✗ ${errorMsg ?: "Parse error"}",
                    color = colors.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
