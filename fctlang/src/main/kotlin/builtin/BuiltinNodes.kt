package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import generated.*
import language.MutableEntity

open class ValueTypesNode : ValuesNode(), ValueTypesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = this
}

fun TermNode.isInValueTypes(): Boolean =
    this::class in setOf(ValueTypesNode::class, ValuesNode::class) || this is ValueTypesNode

open class ValuesNode : TermNode(), ValuesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = this
}

fun TermNode.isInValues(): Boolean = true

open class EmptyTypeNode : ValueTypesNode(), EmptyTypeInterface

fun TermNode.isInEmptyType(): Boolean = false

open class GroundValuesNode : ValueTypesNode(), GroundValuesInterface

fun TermNode.isInGroundValues(): Boolean = this is GroundValuesNode

open class IntegersNode : GroundValuesNode(), IntegersInterface

fun TermNode.isInIntegers(): Boolean = this is IntegersNode

open class CharactersNode : GroundValuesNode(), CharactersInterface

open class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

fun TermNode.isInDatatypeValues(): Boolean = this is DatatypeValuesNode

open class MapsNode(var tp0: TermNode, var tp1: TermNode) : ValueTypesNode(), MapsInterface

fun TermNode.isInMaps(): Boolean = this is ValueMapNode

open class IntegersFromNode(@Child override var p0: TermNode) : IntegersNode(), IntegersFromInterface

class ComputationTypesNode() : ValueTypesNode(), ComputationTypesInterface
class AbstractionNode(@Node.Child override var p0: TermNode) : AbstractionsNode(ComputationTypesNode()),
    AbstractionInterface

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
        return createNewNode(*newTerms.toTypedArray())
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
        return when {
            p0.size == 0 -> p1
            p0.isReducible() -> {
                val r = p0.reduce(frame).toSequence()
                SequentialNode(r, p1)
            }

            p0.size >= 1 && p0.head is NullValueNode -> SequentialNode(p0.tail, p1)
            else -> FailNode()
        }
    }
}

class ChoiceNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), ChoiceInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            p0.size >= 1 -> p0.random()
            else -> FailNode()
        }
    }
}

class IntegerAddNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), IntegerAddInterface {
    override val eager = listOf(0)
    override fun reduceRules(frame: VirtualFrame): TermNode {
        // TODO Check type
        val sum = p0.elements.fold(0) { acc, node -> acc + (node.value as Int) }
        return IntegerNode(sum)
    }
}

class ValueTupleNode(@Node.Child var p0: SequenceNode = SequenceNode()) : TuplesNode() {
    override val value get() = "tuple(${p0.value})"
}

class ValueListNode(@Node.Child var p0: SequenceNode = SequenceNode()) : ListsNode(ValuesNode()) {
    override val value get() = "[${p0.value}]"
}

open class Identifiers : DatatypeValuesNode(), IdentifiersInterface

class IdentifierTaggedNode(override val p0: TermNode, override val p1: TermNode) : Identifiers(),
    IdentifierTaggedInterface

fun TermNode.isInIdentifiers(): Boolean = this is StringNode || this is IdentifierTaggedNode

//class ValueFunctionNode(@Child var p0: TermNode) : FunctionsNode(ValuesNode(), ValuesNode()) {
//    override val value get() = "fun ${p0.value}"
//}

class MapNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), MapInterface {
    override val eager = listOf(0)
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return ValueMapNode(p0)
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

class MapLookupNode(@Child override var p0: TermNode, @Child override var p1: TermNode) : TermNode(),
    MapLookupInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(0) is ValueMapNode && get(1) is GroundValuesNode -> {
                get(0).get(0).elements.firstOrNull { element -> element == get(1) } ?: SequenceNode()
            }

            else -> FailNode()
        }
    }
}

class MapOverrideNode(override val p0: SequenceNode) : TermNode(), MapOverrideInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val resultMap = linkedMapOf<TermNode, TermNode>()

        for (mapNode in p0.elements) {
            mapNode as ValueMapNode
            for (tuple in mapNode.p0.elements) {
                tuple as ValueTupleNode
                require(tuple.p0.size == 2) { "Invalid map entry" }

                val key = tuple.p0.elements[0]
                val value = tuple.p0.elements[1]

                if (!resultMap.containsKey(key)) {
                    resultMap[key] = value
                }
            }
        }

        val tuples = resultMap.entries.map { (k, v) ->
            ValueTupleNode(SequenceNode(k, v))
        }.toTypedArray()

        return ValueMapNode(SequenceNode(*tuples))
    }
}


//class MapDomainNode(@Child override var p0: TermNode) : TermNode(), MapDomainInterface {
//    override fun reduceRules(frame: VirtualFrame): TermNode {
//        return when {
//            get(0) is ValueMapNode && get(1) is GroundValuesNode -> {
//                get(0).get(0).elements.firstOrNull { element -> element == get(1) } ?: SequenceNode()
//            }
//
//            else -> FailNode()
//        }
//    }
//}

class IdentifiersNode() : DatatypeValuesNode(), IdentifiersInterface

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
        val standardIn = getGlobal("standard-in") as? StandardInEntity ?: StandardInEntity()
        val stdInHead = standardIn.p0.popFirst()

        return when (stdInHead) {
            !is NullTypeNode -> stdInHead
            else -> FailNode()
        }
    }
}

open class AtomsNode : ValueTypesNode(), AtomsInterface

class UsedAtomSetEntity(var p0: TermNode) : MutableEntity(p0.toSequence())

class InitialiseGeneratingNode(override val p0: TermNode) : TermNode(), InitialiseGeneratingInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val environment = getGlobal("used-atom-set") as? UsedAtomSetEntity ?: UsedAtomSetEntity(SequenceNode())
        return p0
    }
}

open class UnionTypeNode(@Children vararg var types: TermNode) : ValueTypesNode()

open class IntersectionTypeNode(@Children vararg var types: TermNode) : ValueTypesNode()

open class ComplementTypeNode(@Child var type: TermNode) : ValueTypesNode()