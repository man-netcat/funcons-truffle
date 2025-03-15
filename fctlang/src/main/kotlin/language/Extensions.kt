package language

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

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

open class SequenceNode(@Children vararg var elements: TermNode) : TermNode() {
    init {
        elements = elements.flatMap {
            if (it is SequenceNode) it.elements.toList() else listOf(it)
        }.toTypedArray()
    }

    override fun execute(frame: VirtualFrame): SequenceNode {
        val i = elements.indexOfFirst { it !is ValuesNode }
        val newElements = elements.mapIndexed { index, node ->
            if (index == i) node.execute(frame) else node
        }.toTypedArray()

        val new = SequenceNode(*newElements)


        return replace(new)
    }

    val size: Int
        get() {
            return elements.size
        }

    fun isEmpty(): Boolean {
        return elements.isEmpty()
    }

    fun hasNonValuesNode(): Boolean {
        return elements.any {
            when (it) {
                is SequenceNode -> it.hasNonValuesNode()
                else -> it !is ValuesNode
            }
        }
    }

    operator fun get(index: Int): TermNode {
        return elements[index]
    }

    val head: TermNode
        get() {
            require(elements.isNotEmpty())
            return get(0)
        }

    val tail: SequenceNode
        get() {
            require(elements.isNotEmpty())
            val sliced = elements.sliceFrom(1)
            return SequenceNode(*sliced)
        }

    fun random(): TermNode {
        require(elements.isNotEmpty())
        return elements.random()
    }

    override val value: Any
        get() {
            return elements.joinToString { it.value.toString() }
        }
}


@CBSFuncon
class ValueTupleNode(@Child var p0: SequenceNode) : TuplesNode() {
    override fun execute(frame: VirtualFrame): TermNode {
        return this
    }

    override val value: Any
        get() {
            return "tuple(${p0.value})"
        }
}