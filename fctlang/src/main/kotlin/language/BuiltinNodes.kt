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
//class AbstractionNode(@Child override var p0: TermNode) : AbstractionsNode(), AbstractionInterface

@CBSFuncon
class StuckNode() : TermNode(), StuckInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = abort("stuck")
}

@CBSFuncon
class LeftToRightNode(@Child override var p0: SequenceNode) : TermNode(), LeftToRightInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size == 0 -> SequenceNode()
            p0.isReducible() -> {
                val r = p0.reduce(frame).toSequence()
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
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size == 0 -> SequenceNode()
            p0.isReducible() -> {
                val r = p0.reduceRulesReversed(frame)
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
class ChoiceNode(@Child override var p0: SequenceNode) : TermNode(), ChoiceInterface {
    override val reducibles = listOf(0)

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size >= 1 -> p0.random()
            else -> abort("choice")
        }
        return replace(new)
    }
}

@CBSFuncon
class IntegerAddNode(@Child override var p0: SequenceNode) : TermNode(), IntegerAddInterface {
    override val reducibles = listOf(0)
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
class EmptySequenceNode() : ValuesNode()

@Builtin
class ValueTupleNode(@Child var p0: SequenceNode) : TuplesNode() {
    override val value get() = "tuple(${p0.value})"
}

@Builtin
class ValueListNode(@Child var p0: SequenceNode) : TuplesNode() {
    override val value get() = "[${p0.value}]"
}

@CBSFuncon
class MapNode(@Child override var p0: SequenceNode) : TermNode(), MapInterface {
    override val reducibles = listOf(0)
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = ValueMapNode(p0)
        return replace(new)
    }
}

@Builtin
class ValueMapNode(@Child var p0: SequenceNode) : ValuesNode() {
    override val value
        get() = "{${
            p0.elements.joinToString { tuple ->
                tuple as ValueTupleNode
                require(tuple.p0.size == 2) { "Invalid map" }
                "${tuple.p0.elements[0].value} -> ${tuple.p0.elements[1].value}"
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
}
