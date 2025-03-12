package language

import generated.IntegersNode
import generated.NaturalNumbersNode
import generated.StringsNode

data class NaturalNumberNode(override val value: Int) : NaturalNumbersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NaturalNumberNode -> this.value == other.value
        is IntegerNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

fun Int.toNaturalNumberNode(): NaturalNumbersNode {
    if (this < 0) throw IllegalArgumentException("Natural numbers cannot be negative.")
    return NaturalNumberNode(this)
}

fun NaturalNumbersNode.toInt(): Int = when (this) {
    is NaturalNumberNode -> this.value
    else -> throw IllegalArgumentException("Unsupported NaturalNumbersNode type")
}

data class IntegerNode(override val value: Int) : IntegersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NaturalNumberNode -> this.value == other.value
        is IntegerNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

fun Int.toIntegerNode(): IntegersNode {
    return IntegerNode(this)
}

fun IntegersNode.toInt(): Int = when (this) {
    is IntegerNode -> this.value
    else -> throw IllegalArgumentException("Unsupported IntegersNode type")
}

data class StringNode(override val value: String) : StringsNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is StringNode -> this.value == other.value
        else -> false
    }
}

fun String.toStringNode(): StringsNode = StringNode(this)

fun StringsNode.toStringLiteral(): String = when (this) {
    is StringNode -> this.value
    else -> throw IllegalArgumentException("Unsupported StringsNode type")
}