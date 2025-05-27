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

abstract class NumberNode(override val value: Number) : IntegersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NumberNode -> this.value.toLong() == other.value.toLong()
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString()
}

class NaturalNumberNode(override val value: Int) : NumberNode(value)
class IntegerNode(override val value: Int) : NumberNode(value)

class IntegerAddNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), IntegerAddInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (p0.elements.any { !it.isInIntegers() }) abort()
        val sum = p0.elements.sumOf { it.value as Int }
        return intNode(sum)
    }
}

class IntegerSubtractNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerSubtractInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers() || !p1.isInIntegers()) abort()
        return intNode((p0.value as Int) - (p1.value as Int))
    }
}

class IntegerMultiplyNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(),
    IntegerMultiplyInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (p0.elements.any { !it.isInIntegers() }) abort()
        val product = p0.elements.fold(1) { acc, node -> acc * (node.value as Int) }
        return intNode(product)
    }
}

class IntegerDivideNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerDivideInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers() || !p1.isInIntegers()) abort()
        if ((p1.value as Int) == 0) return SequenceNode()
        return intNode((p0.value as Int) / (p1.value as Int))
    }
}

class IntegerModuloNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerModuloInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers() || !p1.isInIntegers()) abort()
        if ((p1.value as Int) == 0) return SequenceNode()
        return intNode((p0.value as Int) % (p1.value as Int))
    }
}

class IntegerPowerNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerPowerInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers() || !p1.isInIntegers()) abort()
        val base = p0.value as Int
        val exponent = p1.value as Int
        if (exponent < 0) abort()
        return intNode(base.toDouble().pow(exponent).toInt())
    }
}

class IntegerAbsoluteValueNode(@Eager @Child override var p0: TermNode) : TermNode(), IntegerAbsoluteValueInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers()) abort()
        return intNode(abs(p0.value as Int))
    }
}

class IntegerIsLessNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerIsLessInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers() || !p1.isInIntegers()) abort()
        return if ((p0.value as Int) < (p1.value as Int)) TrueNode() else FalseNode()
    }
}

class IntegerIsGreaterNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsGreaterInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers() || !p1.isInIntegers()) abort()
        return if ((p0.value as Int) > (p1.value as Int)) TrueNode() else FalseNode()
    }
}

class IntegerIsLessOrEqualNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsLessOrEqualInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers() || !p1.isInIntegers()) abort()
        return if ((p0.value as Int) <= (p1.value as Int)) TrueNode() else FalseNode()
    }
}

class IntegerIsGreaterOrEqualNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsGreaterOrEqualInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers() || !p1.isInIntegers()) abort()
        return if ((p0.value as Int) >= (p1.value as Int)) TrueNode() else FalseNode()
    }
}

class NaturalSuccessorNode(@Eager @Child override var p0: TermNode) : TermNode(), NaturalSuccessorInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return intNode((p0.value as Int) + 1)
    }
}

class NaturalPredecessorNode(@Eager @Child override var p0: TermNode) : TermNode(), NaturalPredecessorInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val value = p0.value as Int
        return if (value == 0) SequenceNode() else intNode(value - 1)
    }
}

abstract class BaseConversionNode(
    @Eager @Child var p0: TermNode,
    val base: Int,
) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers()) abort()
        val numberStr = (p0.value as Int).toString(base)
        val digits = numberStr.map { intNode(it.digitToInt()) }
        return SequenceNode(*digits.toTypedArray())
    }
}

class BinaryNaturalNode(p0: TermNode) : BaseConversionNode(p0, 2), BinaryNaturalInterface
class OctalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 8), OctalNaturalInterface
class DecimalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 10), DecimalNaturalInterface
class HexadecimalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 16), HexadecimalNaturalInterface
