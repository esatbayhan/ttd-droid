package dev.bayhan.ttd.droid.smartlist

data class SmartList(
    val name: String,
    val icon: String? = null,
    val description: String? = null,
    val order: Int = 0,
    val conditions: List<FilterBlock> = emptyList(),
    val sorts: List<Directive> = emptyList(),
    val groups: List<Directive> = emptyList(),
    val prefills: List<Prefill> = emptyList()
)

data class FilterBlock(
    val conditions: List<Condition>
)

sealed class Condition
data class DateCondition(
    val field: DateField,
    val op: CompareOp,
    val value: DateValue
) : Condition()

data class PriorityCondition(
    val op: PriorityOp,
    val value: Char
) : Condition()

data class TextCondition(
    val field: TextField,
    val op: TextOp,
    val value: String
) : Condition()

data class ExistsCondition(
    val has: Boolean,
    val field: Field
) : Condition()

data class DoneCondition(
    val done: Boolean
) : Condition()

data class Directive(
    val field: String,
    val ascending: Boolean = true
)

data class Prefill(
    val field: String,
    val value: String
)

enum class DateField { DUE, SCHEDULED, STARTING, UPDATED, CREATION_DATE }
enum class TextField { PROJECT, CONTEXT, DESCRIPTION }
enum class Field { PRIORITY, DUE, SCHEDULED, STARTING, UPDATED, CREATION_DATE, PROJECT, CONTEXT, DESCRIPTION }

enum class CompareOp { EQ, NEQ, LT, LTE, GT, GTE }
enum class PriorityOp { ABOVE, BELOW, EQ }
enum class TextOp { INCLUDES, EXCLUDES, EQUALS }

data class DateValue(
    val anchor: DateAnchor,
    val offset: Int = 0,
    val anchorDate: String? = null
)

enum class DateAnchor { TODAY, ABSOLUTE }
