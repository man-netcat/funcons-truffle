package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class ValueTypesNode : ValuesNode(), ValueTypesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = abort("value-types")
}

fun TermNode.isInValueTypes(): Boolean =
    this is ValueTypesNode || this::class == ValuesNode::class

open class ValuesNode : TermNode(), ValuesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = abort("values")
}

fun TermNode.isInValues(): Boolean = this is ValuesNode

open class EmptyTypeNode : ValueTypesNode(), EmptyTypeInterface

fun TermNode.isInEmptyType(): Boolean = false

open class GroundValuesNode : ValueTypesNode(), GroundValuesInterface

fun TermNode.isInGroundValues(): Boolean = this is GroundValuesNode

open class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

class DatatypeValueNode(@Eager @Child var p0: TermNode, @Eager @Child var p1: SequenceNode) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return ValueDatatypeValueNode(p0, p1)
    }
}

fun TermNode.isInDatatypeValues(): Boolean = this is DatatypeValuesNode

class ComputationTypesNode() : ValueTypesNode(), ComputationTypesInterface

fun TermNode.isInComputationTypes(): Boolean = this !is ValueTypesNode && this !is ValuesNode

class AbstractionNode(@Child override var p0: TermNode) : AbstractionsNode(ComputationTypesNode()),
    AbstractionInterface {
    override fun toString(): String = "abstraction(${p0})"
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
            get(0).isEmpty() -> get(1)
            get(0).head.isReducible() -> {
                val s0 = get(0).head.reduce(frame)
                SequentialNode(SequenceNode(s0, get(0).tail), get(1))
            }

            get(0).head is NullValueNode -> SequentialNode(get(0).tail, get(1))

            else -> abort("sequential")
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

class ElseChoiceNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), ElseChoiceInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val shuffled = p0.shuffled()

        return when (shuffled.size) {
            0 -> FailNode()
            1 -> shuffled[0]
            else -> shuffled.drop(1).fold(shuffled[0]) { acc, term ->
                ElseNode(term, SequenceNode(acc))
            }
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
    (this is ValueListNode && this.p0.elements.all { it.isInCharacters() }) || this is Identifiers

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

fun TermNode.isInAtoms(): Boolean = this is AtomsNode

data class AtomNode(override val value: String) : AtomsNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is AtomNode -> this.value == other.value
        else -> false
    }

    override fun toString(): String = "atom($value)"
    override fun hashCode(): Int = value.hashCode()
}

class InitialiseGeneratingNode(@Child override var p0: TermNode) : TermNode(), InitialiseGeneratingInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        putEntity(frame, "used-atom-set", ValueSetNode(SequenceNode()))
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
        TODO("Not yet implemented: $name")
    }
}

class MatchLooselyNode(override val p0: TermNode, override val p1: TermNode) : TermNode(), MatchLooselyInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        TODO("Not yet implemented: $name")
    }
}

class ToStringNode(override val p0: TermNode) : TermNode(), ToStringInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        TODO("Not yet implemented: $name")
    }
}

class AtomicNode(override val p0: TermNode) : TermNode(), AtomicInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        TODO("Not yet implemented: $name")
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