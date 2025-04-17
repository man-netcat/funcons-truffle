package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class ValueTypesNode : ValuesNode(), ValueTypesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = this
}

fun TermNode.isInValueTypes(): Boolean =
    this::class in setOf(ValueTypesNode::class, ValuesNode::class) || this is ValueTypesNode

open class ValuesNode : TermNode(), ValuesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = this
}

fun TermNode.isInValues(): Boolean = this is ValuesNode

open class EmptyTypeNode : ValueTypesNode(), EmptyTypeInterface

fun TermNode.isInEmptyType(): Boolean = false

open class GroundValuesNode : ValueTypesNode(), GroundValuesInterface

fun TermNode.isInGroundValues(): Boolean = this is GroundValuesNode

open class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

class DatatypeValueNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: SequenceNode) :
    DatatypeValuesNode(), DatatypeValueInterface {
    override val value: Any = "datatype-value(${p0.value},${p1.value})"
}

fun TermNode.isInDatatypeValues(): Boolean = this is DatatypeValuesNode

class ComputationTypesNode() : ValueTypesNode(), ComputationTypesInterface

class AbstractionNode(@Child override var p0: TermNode) : AbstractionsNode(ComputationTypesNode()),
    AbstractionInterface {
    override val value: Any = "abstraction(${p0.value})"
}

fun TermNode.isInAbstractions(): Boolean = this is AbstractionNode

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
            p0.isReducible() -> {
                val r = p0.reduce(frame) as SequenceNode
                SequentialNode(r, p1)
            }

            else -> when {
                p0.size > 1 && p0.head is NullValueNode -> SequentialNode(p0.tail, p1)
                else -> p1
            }
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

open class Identifiers : DatatypeValuesNode(), IdentifiersInterface

open class IdentifierTaggedNode(
    @Eager @Child override var p0: TermNode,
    @Eager @Child override var p1: TermNode,
) : TermNode(), IdentifierTaggedInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return ValueIdentifierTaggedNode(p0, p1)
    }
}

fun TermNode.isInIdentifiers(): Boolean =
    (this is ValueListNode && this.p0.elements.all { it is CharacterNode }) || this is IdentifierTaggedNode

class IdentifiersNode() : DatatypeValuesNode(), IdentifiersInterface

class ReadNode : TermNode(), ReadInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val standardIn = getEntity(frame, "standard-in") as SequenceNode
        val stdInHead = standardIn.popFirst()

        return when (stdInHead) {
            !is NullTypeNode -> stdInHead
            else -> FailNode()
        }
    }
}

class PrintNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), PrintInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        appendEntity(frame, "standard-out", get(0))
        return NullValueNode()
    }
}

open class AtomsNode : ValueTypesNode(), AtomsInterface

fun TermNode.isInAtoms(): Boolean = this is AtomNode

data class AtomNode(override val value: String) : AtomsNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is AtomNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

class InitialiseGeneratingNode(@Child override var p0: TermNode) : TermNode(), InitialiseGeneratingInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        putEntity(frame, "used-atom-set", ValueSetNode(SequenceNode(AtomNode("initatom"))))
//        putEntity(frame, "used-atom-set", ValueSetNode(SequenceNode(AtomNode("a0"))))
//        putEntity(frame, "used-atom-set", ValueSetNode(SequenceNode()))
        return p0
    }
}

open class UnionTypeNode(@Children vararg var types: TermNode) : ValueTypesNode()

open class IntersectionTypeNode(@Children vararg var types: TermNode) : ValueTypesNode()

open class ComplementTypeNode(@Child var type: TermNode) : ValueTypesNode()

class HoleNode() : TermNode(), HoleInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val plugSignal = getEntity(frame, "plug-signal")
        return when {
            plugSignal.isNotEmpty() -> plugSignal
            else -> FailNode()
        }
    }
}

class MatchNode(override val p0: TermNode, override val p1: TermNode) : TermNode(), MatchInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        TODO("Not yet implemented")
    }
}

class MatchLooselyNode(override val p0: TermNode, override val p1: TermNode) : TermNode(), MatchLooselyInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        TODO("Not yet implemented")
    }
}

class ToStringNode(override val p0: TermNode) : TermNode(), ToStringInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        TODO("Not yet implemented")
    }
}

open class PointersNode(@Eager @Child var tp0: TermNode) : DatatypeValuesNode(), PointersInterface

class PointerNullNode : PointersNode(ValuesNode()), PointerNullInterface

fun TermNode.isInPointers(): Boolean {
    return this::class in setOf(
        PointerNullNode::class,
        ReferenceNode::class
    )
}