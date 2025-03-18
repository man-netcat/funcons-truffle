package language

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

@CBSType
abstract class ValuesNode : TermNode(), ValuesInterface {
    override fun reduce(frame: VirtualFrame): TermNode {
        return this
    }
}

@CBSType
abstract class GroundValuesNode : ValuesNode(), GroundValuesInterface

@CBSType
abstract class ValueTypesNode : ValuesNode(), ValueTypesInterface

@CBSType
final class EmptyTypeNode : ValuesNode(), EmptyTypeInterface

@CBSType
abstract class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

@CBSType
abstract class IntegersNode : ValueTypesNode(), IntegersInterface

@CBSType
abstract class CharactersNode : ValueTypesNode(), CharactersInterface

@CBSType
abstract class IntegersFromNode(@Child override var p0: TermNode) : IntegersNode(), IntegersFromInterface

@CBSFuncon
class StuckNode() : TermNode(), StuckInterface {
    override fun reduce(frame: VirtualFrame): TermNode {
        abort("stuck")
    }
}

@CBSFuncon
class LeftToRightNode(@Child override var p0: SequenceNode) : TermNode(), LeftToRightInterface {
    override fun reduce(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size == 0 -> SequenceNode()
            p0.isReducible() -> {
                val r = p0.reduce(frame)
                LeftToRightNode(r)
            }

            !p0.isReducible() -> p0
            else -> abort("left-to-right")
        }

        return replace(new)
    }
}

@CBSFuncon
class RightToLeftNode(@Child override var p0: SequenceNode) : TermNode(), RightToLeftInterface {
    override fun reduce(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size == 0 -> SequenceNode()
            p0.isReducible() -> {
                val r = p0.reduceReverse(frame)
                RightToLeftNode(r)
            }

            !p0.isReducible() -> p0
            else -> abort("left-to-right")
        }

        return replace(new)
    }
}

@CBSFuncon
class SequentialNode(@Child override var p0: SequenceNode, @Child override var p1: TermNode) : TermNode(),
    SequentialInterface {
    override fun reduce(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size == 0 -> p1
            p0.isReducible() -> {
                val r = p0.reduce(frame)
                SequentialNode(r, p1)
            }

            p0.size >= 1 && p0.head is NullValueNode -> SequentialNode(p0.tail, p1)
            else -> abort("sequential")
        }
        return replace(new)
    }
}

@CBSFuncon
class ChoiceNode(@Child override var p0: SequenceNode) : TermNode(), ChoiceInterface {
    override fun reduce(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size >= 1 -> p0.random()
            else -> abort("choice")
        }
        return replace(new)
    }
}

@CBSFuncon
class IntegerAddNode(@Child override var p0: SequenceNode) : TermNode(), IntegerAddInterface {
    override fun reduce(frame: VirtualFrame): TermNode {
        val new = when {
            p0.isReducible() -> {
                val r = p0.reduce(frame)
                IntegerAddNode(r)
            }

            p0.size >= 1 -> {
                val sum = p0.elements.fold(0) { acc, node ->
                    node.value as Int
                }
                IntegerNode(sum)
            }

            p0.size == 0 -> IntegerNode(0)

            else -> abort("integer-add")
        }

        return replace(new)
    }
}

@Builtin
class EmptySequenceNode() : ValuesNode()

@Builtin
open class SequenceNode(@Children vararg var elements: TermNode) : TermNode() {
    init {
        elements = elements.flatMap {
            if (it is SequenceNode) it.elements.toList() else listOf(it)
        }.toTypedArray()
    }

    fun reduceReverse(frame: VirtualFrame): SequenceNode {
        val i = elements.indexOfLast { it !is ValuesNode }
        val newElements = elements.mapIndexed { index, node ->
            if (index == i) node.reduce(frame) else node
        }.toTypedArray()

        val new = SequenceNode(*newElements)


        return replace(new)
    }

    override fun reduce(frame: VirtualFrame): SequenceNode {
        val i = elements.indexOfFirst { it !is ValuesNode }
        val newElements = elements.mapIndexed { index, node ->
            if (index == i) node.reduce(frame) else node
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

    operator fun get(index: Int): TermNode {
        return elements[index]
    }

    fun sliceFrom(n: Int): SequenceNode {
        val sliced = elements.sliceFrom(n)
        return SequenceNode(*sliced)
    }

    val head: TermNode get() = get(0)
    val tail: SequenceNode get() = sliceFrom(1)


    fun random(): TermNode {
        require(elements.isNotEmpty())
        return elements.random()
    }

    override val value: Any
        get() {
            return elements.joinToString { it.value.toString() }
        }

    override fun toString(): String {
        return elements.joinToString("") { it.value.toString() }
    }
}


@Builtin
class ValueTupleNode(@Child var p0: SequenceNode) : TuplesNode() {
    override fun reduce(frame: VirtualFrame): TermNode {
        return this
    }

    override val value: Any
        get() = "tuple(${p0.value})"
}

@Builtin
data class NaturalNumberNode(override val value: Int) : NaturalNumbersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NaturalNumberNode -> this.value == other.value
        is IntegerNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

fun NaturalNumbersNode.toInt(): Int = when (this) {
    is NaturalNumberNode -> this.value
    else -> throw IllegalArgumentException("Unsupported NaturalNumbersNode type")
}

@Builtin
data class IntegerNode(override val value: Int) : IntegersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NaturalNumberNode -> this.value == other.value
        is IntegerNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

fun IntegersNode.toInt(): Int = when (this) {
    is IntegerNode -> this.value
    else -> throw IllegalArgumentException("Unsupported IntegersNode type")
}

@Builtin
data class StringNode(override val value: String) : StringsNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is StringNode -> this.value == other.value
        else -> false
    }
}

fun StringsNode.toStringLiteral(): String = when (this) {
    is StringNode -> this.value
    else -> throw IllegalArgumentException("Unsupported StringsNode type")
}

fun newNaturalNumberNode(value: Int): NaturalNumbersNode {
    if (value < 0) throw IllegalArgumentException("Natural numbers cannot be negative.")
    return NaturalNumberNode(value)
}

fun newIntegerNode(value: Int): IntegersNode {
    return IntegerNode(value)
}

fun newStringNode(value: String): StringsNode {
    return StringNode(value)
}
