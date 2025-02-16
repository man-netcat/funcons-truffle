package interpreter

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class NaturalNumberNode(val value: Int) : NaturalNumbersNode()

fun Int.toNaturalNumberNode(): NaturalNumbersNode {
    if (this < 0) throw IllegalArgumentException("Natural numbers cannot be negative.")
    return NaturalNumberNode(this)
}

fun NaturalNumbersNode.toInt(): Int {
    return when (this) {
        is NaturalNumberNode -> this.value
        else -> throw IllegalArgumentException("Unsupported NaturalNumbersNode type")
    }
}

open class IntegerNode(val value: Int) : IntegersNode()

fun Int.toIntegerNode(): IntegersNode {
    return IntegerNode(this)
}

fun IntegersNode.toInt(): Int {
    return when (this) {
        is IntegerNode -> this.value
        else -> throw IllegalArgumentException("Unsupported IntegersNode type")
    }
}

open class StringNode(val value: String) : StringsNode()

fun String.toStringNode(): StringsNode {
    return StringNode(this)
}

fun StringsNode.toStringLiteral(): String {
    return when (this) {
        is StringNode -> this.value
        else -> throw IllegalArgumentException("Unsupported StringsNode type")
    }
}

fun ValueTypesNode.isInstance(other: ValuesNode): Boolean {
    TODO("Not yet implemented")
}

class SequenceNode(vararg val p0: FCTNode) : FCTNode() {
    override val typeName: String = "sequence"

    override fun execute(frame: VirtualFrame): FCTNode? {
        TODO("Not yet implemented")
    }

    fun toTypedArray(): Array<out FCTNode> = p0
}