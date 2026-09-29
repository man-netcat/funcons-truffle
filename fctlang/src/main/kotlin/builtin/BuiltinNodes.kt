package builtin

import builtin.ValueNodeFactory.strLiteralNode
import com.oracle.truffle.api.frame.VirtualFrame
import generated.*
import language.appendEntity
import language.getEntity
import language.printEntities
import language.putEntity

open class ValueTypesNode : ValuesNode(), ValueTypesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = abort()
}

fun TermNode.isInValueTypes(): Boolean =
    this is ValueTypesNode || this::class == ValuesNode::class

open class ValuesNode : TermNode(), ValuesInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = abort()
}

fun TermNode.isInValues(): Boolean = this is ValuesNode

open class EmptyTypeNode : ValueTypesNode(), EmptyTypeInterface

fun TermNode.isInEmptyType(): Boolean = false

open class GroundValuesNode : ValueTypesNode(), GroundValuesInterface

/*
  Groundness is structural, not a property of the node class. funcons-tools agrees with
  the reference on:

    is-equal(thunk(12), thunk(12))                                    -> true
    is-equal(abstraction 12, abstraction(12))                        -> true
    is-equal(thunk(abstraction(pattern-bind "it")),
             thunk(abstraction(pattern-bind "it")))                   -> false
    is-equal(abstraction(pattern-bind "it"),
             abstraction(pattern-bind "it"))                          -> false
    is-equal(closure(12), closure(12))                               -> false

  So a thunk or an abstraction is ground exactly when the term it wraps is ground, and
  the last case shows it is not "compare non-ground values structurally": two identical
  non-ground thunks are unequal, because neither is ground and the is-equal rules only
  compare ground values. A closure is never ground, since it closes over an environment
  even when its body is ground, which is why it stays unequal above.

  ThunkNode reduces to ValueThunkNode unconditionally, so both spellings are handled.
*/
fun TermNode.isInGroundValues(): Boolean = when (this) {
    is ThunkNode -> p0.isInGroundValues()
    is ValueThunkNode -> vp0.isInGroundValues()
    is AbstractionNode -> p0.isInGroundValues()
    else -> this is GroundValuesNode
}

class ComputationTypesNode() : ValueTypesNode(), ComputationTypesInterface

fun TermNode.isInComputationTypes(): Boolean = this !is ValueTypesNode && this !is ValuesNode

class AbstractionNode(@Child override var p0: TermNode) : AbstractionsNode(ComputationTypesNode()),
    AbstractionInterface {
    override fun toString(): String = "abstraction(${get(0)})"
}

fun TermNode.isInAbstractions(): Boolean = this is AbstractionNode

class StuckNode() : TermNode(), StuckInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = abort()
}

abstract class DirectionalNode(@Children open vararg var p0: SequenceNode) : TermNode() {
    protected abstract fun findReducibleIndex(vararg terms: SequenceNode): Int
    protected abstract fun createNewNode(vararg newTerms: SequenceNode): DirectionalNode

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val reducibleIndex = findReducibleIndex(*p0)
        val newTerms = p0.toMutableList()

        if (reducibleIndex == -1) {
            val flattenedElements = p0.flatMap { it.elements.asList() }.toTypedArray()
            return SequenceNode(*flattenedElements)
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


class TupleElementsNode(@Eager @Child override var p0: TermNode = SequenceNode()) : TermNode(), TupleElementsInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode = when (p0) {
        is ValueTupleNode -> UnpackableTupleNode((p0 as ValueTupleNode).vp0)
        else -> abort()
    }
}

class UnpackableTupleNode(@Child var vp0: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode(strLiteralNode("unpackable-tuple"), SequenceNode(vp0))


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

            get(0).head is ValueNullValueNode -> SequentialNode(get(0).tail, get(1))

            else -> abort()
        }
    }
}

/**
 * `choice(Y, ...)` "selects one of its arguments" and is "associative and
 * commutative" (Flowing.cbs), so any argument may be taken. Taking the first
 * is one of those orders, and it makes the result reproducible: picking at
 * random makes a program's output depend on the run, and the corpus
 * expectations for `choice.config` are the leftmost order.
 */
class ChoiceNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), ChoiceInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(0).size >= 1 -> get(0).head
            else -> abort()
        }
    }
}

/**
 * `else-choice` executes its arguments "in any order until either some Xi does
 * *not* fail" (Failing.cbs), so left-to-right is a valid order, and it is the
 * one `else-choice.config` states as its expected output.
 */
class ElseChoiceNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), ElseChoiceInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val terms = get(0)
        return when {
            terms.isEmpty() -> abort()
            terms.size == 1 -> terms.head
            terms.size >= 2 -> ElseNode(terms.head, SequenceNode(ElseChoiceNode(terms.tail)))

            else -> abort()
        }
    }
}

class ReadNode : TermNode(), ReadInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val standardIn = getEntity(frame, "standard-in") as SequenceNode
        val stdInHead = standardIn.popFirst()
        if (standardIn.isNotEmpty()) putEntity(frame, "standard-in", standardIn.tail)

        return when {
            !stdInHead.isInNullType() -> stdInHead
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

class InitialiseGeneratingNode(@Child override var p0: TermNode) : TermNode(), InitialiseGeneratingInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        putEntity(frame, "used-atom-set", ValueSetNode(SequenceNode()))
        return get(0)
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
            else -> abort()
        }
    }
}

class ToStringNode(@Eager @Child override var p0: TermNode) : TermNode(), ToStringInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(0).isInStrings() -> get(0)
            else -> StringLiteralNode(get(0).toString())
        }
    }
}

class AtomicNode(@Child override var p0: TermNode) : TermNode(), AtomicInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(0).isReducible() -> {
                val s0 = get(0).reduce(frame)
                val yielded = getEntity(frame, "yielded")
                when {
                    yielded.isEmpty() -> {
                        putEntity(frame, "yielded", SequenceNode())
                        when {
                            s0.isReducible() -> {
                                val s1 = s0.reduce(frame)
                                val yielded2 = getEntity(frame, "yielded")
                                when {
                                    yielded2.isEmpty() -> {
                                        putEntity(frame, "yielded", SequenceNode())
                                        s1
                                    }

                                    else -> abort()
                                }
                            }

                            s0.isInValues() -> {
                                putEntity(frame, "yielded", SequenceNode())
                                s0
                            }

                            else -> abort()
                        }
                    }

                    yielded is SignalNode -> {
                        putEntity(frame, "yielded", SequenceNode())
                        AtomicNode(s0)
                    }

                    else -> abort()
                }
            }

            get(0).isInValues() -> getCopy(0)
            else -> abort()
        }
    }
}

class DebugNode(@Child var p0: TermNode) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            p0.isInValues() -> p0

            p0.isReducible() -> {
                p0.printTree()
                printEntities(frame)
                println("reducing: ${p0::class.simpleName} with params ${p0.params.map { it::class.simpleName }}")
                val s0 = p0.reduce(frame)
                println("replacing: ${p0::class.simpleName} for ${s0::class.simpleName}")
                DebugNode(s0)
            }

            else -> abort()
        }
    }
}