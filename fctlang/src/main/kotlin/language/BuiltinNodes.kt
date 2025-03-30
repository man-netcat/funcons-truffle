package language

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

@CBSType
open class ValuesNode : TermNode(), ValuesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = this
}

@CBSType
open class GroundValuesNode : ValueTypesNode(), GroundValuesInterface

@CBSType
open class ValueTypesNode : ValuesNode(), ValueTypesInterface

@CBSType
final class EmptyTypeNode : ValuesNode(), EmptyTypeInterface

@CBSType
open class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

@CBSType
open class IntegersNode : GroundValuesNode(), IntegersInterface

@CBSType
open class CharactersNode : GroundValuesNode(), CharactersInterface

@CBSType
open class MapsNode : ValueTypesNode(), MapsInterface

@CBSType
open class IntegersFromNode(@Child override var p0: TermNode) : IntegersNode(), IntegersFromInterface

//@CBSFuncon
class AbstractionNode(@Child override var p0: TermNode) : AbstractionsNode(), AbstractionInterface

@CBSFuncon
class StuckNode() : TermNode(), StuckInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = abort("stuck")
}

abstract class DirectionalNode(@Children open vararg var p0: SequenceNode) : TermNode() {
    protected abstract fun findReducibleIndex(vararg terms: SequenceNode): Int

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val reducibleIndex = findReducibleIndex(*p0)
        val newTerms = p0.toMutableList()

        if (reducibleIndex == -1) {
            val flattenedElements = p0.flatMap { it.elements.asList() }.toTypedArray()
            return replace(SequenceNode(*flattenedElements))
        }

        newTerms[reducibleIndex] = newTerms[reducibleIndex].reduce(frame) as SequenceNode
        val new = createNewNode(*newTerms.toTypedArray())
        return replace(new)
    }

    protected abstract fun createNewNode(vararg newTerms: SequenceNode): DirectionalNode
}

@CBSFuncon
class LeftToRightNode(@Children override vararg var p0: SequenceNode) : DirectionalNode(*p0), LeftToRightInterface {
    override fun findReducibleIndex(vararg terms: SequenceNode) = terms.indexOfFirst { it.isReducible() }
    override fun createNewNode(vararg newTerms: SequenceNode) = LeftToRightNode(*newTerms)
}

@CBSFuncon
class RightToLeftNode(@Children override vararg var p0: SequenceNode) : DirectionalNode(*p0), RightToLeftInterface {
    override fun findReducibleIndex(vararg terms: SequenceNode) = terms.indexOfLast { it.isReducible() }
    override fun createNewNode(vararg newTerms: SequenceNode) = RightToLeftNode(*newTerms)
}

@CBSFuncon
class SequentialNode(
    @Child override var p0: SequenceNode = SequenceNode(),
    @Child override var p1: TermNode,
) :
    TermNode(),
    SequentialInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size == 0 -> p1
            p0.isReducible() -> {
                val r = p0.reduce(frame).toSequence()
                SequentialNode(r, p1)
            }

            p0.size >= 1 && p0.head is NullValueNode -> SequentialNode(p0.tail, p1)
            else -> abort("sequential")
        }
        return replace(new)
    }
}

@CBSFuncon
class ChoiceNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), ChoiceInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size >= 1 -> p0.random()
            else -> abort("choice")
        }
        return replace(new)
    }
}

@CBSFuncon
class IntegerAddNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), IntegerAddInterface {
    override val nonLazy = listOf(0)
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size >= 0 -> {
                val sum = p0.elements.fold(0) { acc, node -> node.value as Int }
                IntegerNode(sum)
            }

            else -> abort("integer-add")
        }

        return replace(new)
    }
}

@Builtin
class ValueTupleNode(@Child var p0: SequenceNode = SequenceNode()) : TuplesNode() {
    override val value get() = "tuple(${p0.value})"
}

@Builtin
class ValueListNode(@Child var p0: SequenceNode = SequenceNode()) : ListsNode() {
    override val value get() = "[${p0.value}]"
}

@CBSFuncon
class MapNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), MapInterface {
    override val nonLazy = listOf(0)
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = ValueMapNode(p0)
        return replace(new)
    }
}

@Builtin
class ValueMapNode(@Child var p0: SequenceNode = SequenceNode()) : ValuesNode() {
    override val value
        get() = "{${
            p0.elements.joinToString { tuple ->
                tuple as ValueTupleNode
                require(tuple.p0.size == 2) { "Invalid map" }
                "${tuple.p0.elements[0].value} |-> ${tuple.p0.elements[1].value}"
            }
        }}"
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

@Builtin
data class IntegerNode(override val value: Int) : IntegersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NaturalNumberNode -> this.value == other.value
        is IntegerNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

@Builtin
data class StringNode(override val value: String) : StringsNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is StringNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

@Builtin
class ReadNode : TermNode(), ReadInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val standardIn = getGlobal("standard-in") as? StandardInNode ?: StandardInNode()
        val stdInHead = standardIn.p0.popFirst()

        val new = when (stdInHead) {
            !is NullTypeNode -> stdInHead
            is NullValueNode -> FailNode()

            else -> abort("read")
        }
        return replace(new)
    }
}

@Builtin
class UnionTypeNode(@Children vararg var types: TermNode) : ValueTypesNode() {
    override fun isTypeOf(other: TermNode): Boolean {
        return types.any { it.isTypeOf(other) }
    }
}

@Builtin
class IntersectionTypeNode(@Children vararg var types: TermNode) : ValueTypesNode() {
    override fun isTypeOf(other: TermNode): Boolean {
        return types.all { it.isTypeOf(other) }
    }
}

@Builtin
class ComplementTypeNode(@Child var type: TermNode) : ValueTypesNode() {
    override fun isTypeOf(other: TermNode): Boolean {
        return !type.isTypeOf(other)
    }
}