package language

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

@CBSType
open class ValuesNode : TermNode(), ValuesInterface {
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
abstract class DatatypeValuesNode : ValueTypesNode(), DatatypeValuesInterface

@CBSType
abstract class IntegersNode : ValueTypesNode(), IntegersInterface

abstract class IntegersFromNode(@Child override var p0: TermNode) : IntegersNode(), IntegersFromInterface

@CBSFuncon
class LeftToRightNode(@Child override var p0: SequenceNode) : TermNode(), LeftToRightInterface {
    override fun reduce(frame: VirtualFrame): TermNode {
        val new = when {
            p0.size == 0 -> SequenceNode()
            p0.hasNonValuesNode() -> {
                val r = p0.reduce(frame)
                LeftToRightNode(r)
            }

            !p0.hasNonValuesNode() -> p0
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
            p0.hasNonValuesNode() -> {
                val r = p0.reduce(frame)
                SequentialNode(r, p1)
            }

            p0.size >= 1 && p0.head is NullValueNode -> SequentialNode(p0.tail, p1)
            p0.size == 0 -> p1
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
            p0.hasNonValuesNode() -> {
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