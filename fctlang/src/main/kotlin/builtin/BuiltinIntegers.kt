package builtin

import builtin.ValueNodeFactory.intNode
import com.oracle.truffle.api.frame.VirtualFrame
import generated.*
import kotlin.math.abs
import kotlin.math.pow

open class IntegersNode : GroundValuesNode(), IntegersInterface

fun TermNode.isInIntegers(): Boolean = this is IntegersNode

open class IntegersFromNode(@Child override var p0: TermNode) : IntegersNode(), IntegersFromInterface
open class IntegersUpToNode(@Child override var p0: TermNode) : IntegersNode(), IntegersUpToInterface

abstract class NumberNode(override val value: Long) : IntegersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NumberNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString()
}

class NaturalNumberNode(override val value: Long) : NumberNode(value)
class IntegerNode(override val value: Long) : NumberNode(value)

private fun TermNode.asLongOrAbort(): Long {
    if (!isInIntegers()) abort()
    return when (value) {
        is Long -> value as Long
        is Int -> (value as Int).toLong()
        else -> abort()
    }
}

private fun Array<out TermNode>.allIntegersOrAbort() {
    forEach { if (!it.isInIntegers()) it.abort() }
}

class IntegerAddNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), IntegerAddInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        p0.elements.allIntegersOrAbort()
        val sum = p0.elements.fold(0L) { acc, node -> acc + node.asLongOrAbort() }
        return intNode(sum)
    }
}

class IntegerSubtractNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerSubtractInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return intNode(p0.asLongOrAbort() - p1.asLongOrAbort())
    }
}

class IntegerMultiplyNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(),
    IntegerMultiplyInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        p0.elements.allIntegersOrAbort()
        val product = p0.elements.fold(1L) { acc, node -> acc * node.asLongOrAbort() }
        return intNode(product)
    }
}

class IntegerDivideNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerDivideInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val dividend = p0.asLongOrAbort()
        val divisor = p1.asLongOrAbort()
        if (divisor == 0L) return SequenceNode()
        return intNode(dividend / divisor)
    }
}

class IntegerModuloNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerModuloInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val dividend = p0.asLongOrAbort()
        val divisor = p1.asLongOrAbort()
        if (divisor == 0L) return SequenceNode()
        return intNode(dividend % divisor)
    }
}

class IntegerPowerNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerPowerInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val base = p0.asLongOrAbort()
        val exponent = p1.asLongOrAbort()
        if (exponent < 0L) abort()
        return intNode(base.toDouble().pow(exponent.toDouble()).toLong())
    }
}

class IntegerAbsoluteValueNode(@Eager @Child override var p0: TermNode) : TermNode(), IntegerAbsoluteValueInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return intNode(abs(p0.asLongOrAbort()))
    }
}

class IntegerIsLessNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerIsLessInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return if (p0.asLongOrAbort() < p1.asLongOrAbort()) TrueNode() else FalseNode()
    }
}

class IntegerIsGreaterNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsGreaterInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return if (p0.asLongOrAbort() > p1.asLongOrAbort()) TrueNode() else FalseNode()
    }
}

class IntegerIsLessOrEqualNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsLessOrEqualInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return if (p0.asLongOrAbort() <= p1.asLongOrAbort()) TrueNode() else FalseNode()
    }
}

class IntegerIsGreaterOrEqualNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsGreaterOrEqualInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return if (p0.asLongOrAbort() >= p1.asLongOrAbort()) TrueNode() else FalseNode()
    }
}

class NaturalSuccessorNode(@Eager @Child override var p0: TermNode) : TermNode(), NaturalSuccessorInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return intNode(p0.asLongOrAbort() + 1L)
    }
}

class NaturalPredecessorNode(@Eager @Child override var p0: TermNode) : TermNode(), NaturalPredecessorInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val value = p0.asLongOrAbort()
        return if (value == 0L) SequenceNode() else intNode(value - 1L)
    }
}

abstract class BaseConversionNode(
    @Eager @Child var p0: TermNode,
    val base: Int,
) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val numberStr = p0.asLongOrAbort().toString(base)
        val digits = numberStr.map { intNode(it.digitToInt().toLong()) }
        return SequenceNode(*digits.toTypedArray())
    }
}

class BinaryNaturalNode(p0: TermNode) : BaseConversionNode(p0, 2), BinaryNaturalInterface
class OctalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 8), OctalNaturalInterface
class DecimalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 10), DecimalNaturalInterface
class HexadecimalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 16), HexadecimalNaturalInterface
