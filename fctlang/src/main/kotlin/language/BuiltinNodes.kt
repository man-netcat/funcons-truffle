package language

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class ValueTypesNode : ValuesNode(), ValueTypesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = this
}

open class ValuesNode : TermNode(), ValuesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = this
}

open class GroundValuesNode : ValuesNode(), GroundValuesInterface

open class IntegersNode : GroundValuesNode(), IntegersInterface {
    companion object {
        fun contains(value: TermNode): Boolean {
            return value is IntegerNode
        }
    }
}

open class CharactersNode : GroundValuesNode(), CharactersInterface

open class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

open class MapsNode(var tp0: TermNode, var tp1: TermNode) : ValuesNode(), MapsInterface

final class EmptyTypeNode : ValueTypesNode(), EmptyTypeInterface

open class IntegersFromNode(@Child override var p0: TermNode) : IntegersNode(), IntegersFromInterface

class ComputationTypesNode() : ValueTypesNode(), ComputationTypesInterface
class AbstractionNode(@Child override var p0: TermNode) : AbstractionsNode(ComputationTypesNode()), AbstractionInterface

class StuckNode() : TermNode(), StuckInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = abort("stuck")
}

abstract class DirectionalNode(@Children open vararg var p0: SequenceNode) : TermNode() {
    protected abstract fun findReducibleIndex(vararg terms: SequenceNode): Int
    protected abstract fun createNewNode(vararg newTerms: SequenceNode): DirectionalNode

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
}

class LeftToRightNode(@Children override vararg var p0: SequenceNode) : DirectionalNode(*p0), LeftToRightInterface {
    override fun findReducibleIndex(vararg terms: SequenceNode) = terms.indexOfFirst { it.isReducible() }
    override fun createNewNode(vararg newTerms: SequenceNode) = LeftToRightNode(*newTerms)
}

class RightToLeftNode(@Children override vararg var p0: SequenceNode) : DirectionalNode(*p0), RightToLeftInterface {
    override fun findReducibleIndex(vararg terms: SequenceNode) = terms.indexOfLast { it.isReducible() }
    override fun createNewNode(vararg newTerms: SequenceNode) = RightToLeftNode(*newTerms)
}

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
            else -> FailNode()
        }
        return replace(new)
    }
}

class ChoiceNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), ChoiceInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size >= 1 -> p0.random()
            else -> FailNode()
        }
        return replace(new)
    }
}

class IntegerAddNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), IntegerAddInterface {
    override val eager = listOf(0)
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val sum = p0.elements.fold(0) { acc, node -> node.value as Int }
        val new = IntegerNode(sum)
        return replace(new)
    }
}

class ValueTupleNode(@Child var p0: SequenceNode = SequenceNode()) : TuplesNode() {
    override val value get() = "tuple(${p0.value})"
}

class ValueListNode(@Child var p0: SequenceNode = SequenceNode()) : ListsNode(ValuesNode()) {
    override val value get() = "[${p0.value}]"
}

class MapNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), MapInterface {
    override val eager = listOf(0)
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = ValueMapNode(p0)
        return replace(new)
    }
}

class ValueMapNode(@Child var p0: SequenceNode = SequenceNode()) : MapsNode(GroundValuesNode(), ValuesNode()) {
    override val value
        get() = "{${
            p0.elements.joinToString { tuple ->
                tuple as ValueTupleNode
                require(tuple.p0.size == 2) { "Invalid map" }
                "${tuple.p0.elements[0].value} |-> ${tuple.p0.elements[1].value}"
            }
        }}"
}

data class NaturalNumberNode(override val value: Int) : NaturalNumbersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NaturalNumberNode -> this.value == other.value
        is IntegerNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

data class IntegerNode(override val value: Int) : IntegersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NaturalNumberNode -> this.value == other.value
        is IntegerNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

data class CharacterNode(override val value: Char) : CharactersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is CharacterNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

data class StringNode(override val value: String) : StringsNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is StringNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

class ReadNode : TermNode(), ReadInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val standardIn = getGlobal("standard-in") as? StandardInNode ?: StandardInNode()
        val stdInHead = standardIn.p0.popFirst()

        val new = when (stdInHead) {
            !is NullTypeNode -> stdInHead
            else -> FailNode()
        }
        return replace(new)
    }
}

open class UnionTypeNode(@Children vararg var types: TermNode) : ValueTypesNode()

open class IntersectionTypeNode(@Children vararg var types: TermNode) : ValueTypesNode()

open class ComplementTypeNode(@Child var type: TermNode) : ValueTypesNode()