package builtin

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

fun TermNode.isInGroundValues(): Boolean = this is GroundValuesNode || this.isInDatatypeValues()

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
            return replace(SequenceNode(*flattenedElements))
        }

        newTerms[reducibleIndex] = newTerms[reducibleIndex].reduce(frame) as SequenceNode
        return replace(createNewNode(*newTerms.toTypedArray()))
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

            get(0).head is ValueNullValueNode -> SequentialNode(get(0).tail, get(1))

            else -> abort()
        }
    }
}

class ChoiceNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), ChoiceInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(0).size >= 1 -> get(0).random()
            else -> abort()
        }
    }
}

class ElseChoiceNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), ElseChoiceInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val terms = get(0)
        return when {
            terms.isEmpty() -> abort()
            terms.size == 1 -> get(0).get(0)
            terms.size >= 2 -> {
                terms.elements.shuffle()
                ElseNode(terms.head, SequenceNode(ElseChoiceNode(terms.tail)))
            }

            else -> abort()
        }
    }
}

class ReadNode : TermNode(), ReadInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val standardIn = getEntity(frame, "standard-in") as SequenceNode
        val stdInHead = standardIn.popFirst()

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

class AtomicNode(override val p0: TermNode) : TermNode(), AtomicInterface {
    @Child
    private lateinit var s0: TermNode

    @Child
    private lateinit var s1: TermNode

    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(0).isReducible() -> {
                s0 = insert(get(0)).reduce(frame)
                val yielded = getEntity(frame, "yielded")
                when {
                    yielded.isEmpty() -> {
                        putEntity(frame, "yielded", SequenceNode())
                        when {
                            s0.isReducible() -> {
                                s1 = insert(s0).reduce(frame)
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