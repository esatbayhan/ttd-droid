package dev.bayhan.ttd.droid.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.bayhan.ttd.droid.smartlist.SmartListParser

data class ListSyntaxColors(
    val delim: Color,
    val comment: Color,
    val keyword: Color,
    val operator: Color,
    val date: Color,
    val directive: Color,
    val orKeyword: Color,
    val template: Color
)

fun buildListHighlightedText(raw: String, colors: ListSyntaxColors) = buildAnnotatedString {
    append(raw)

    fun addPattern(regex: Regex, style: SpanStyle) {
        for (match in regex.findAll(raw)) {
            addStyle(style, match.range.first, match.range.last + 1)
        }
    }

    val delimStyle = SpanStyle(color = colors.delim)
    val commentStyle = SpanStyle(color = colors.comment)
    val keywordStyle = SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold)
    val operatorStyle = SpanStyle(color = colors.operator)
    val dateStyle = SpanStyle(color = colors.date)
    val directiveStyle = SpanStyle(color = colors.directive, fontWeight = FontWeight.Bold)
    val orStyle = SpanStyle(color = colors.orKeyword, fontWeight = FontWeight.Bold)
    val templateStyle = SpanStyle(color = colors.template)

    addPattern(Regex("^---", RegexOption.MULTILINE), delimStyle)
    addPattern(Regex("""^\s*#.*""", RegexOption.MULTILINE), commentStyle)
    addPattern(Regex("""\b(due|scheduled|starting|updated|creation_date|priority|project|context|description)\b"""), keywordStyle)
    addPattern(Regex("""\b(includes|excludes|above|below|has|no|time)\b"""), operatorStyle)
    addPattern(Regex(""">=|<=|!=|[=<>]"""), operatorStyle)
    addPattern(Regex("""not done"""), operatorStyle)
    addPattern(Regex("""\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2})?"""), dateStyle)
    addPattern(Regex("""\{\{dir(?::\d+)?\}\}"""), templateStyle)
    addPattern(Regex("^(sort by|group by|prefill)", RegexOption.MULTILINE), directiveStyle)
    addPattern(Regex("""^OR$""", RegexOption.MULTILINE), orStyle)
    addPattern(Regex("^(name|icon|description|order):", RegexOption.MULTILINE), directiveStyle)
    addPattern(Regex("""(?<=priority )[A-Z]\b"""), operatorStyle)
}

fun parseValidity(raw: String): Pair<Boolean, String?> {
    return try {
        SmartListParser.parse(raw)
        true to null
    } catch (e: Exception) {
        false to e.message
    }
}
