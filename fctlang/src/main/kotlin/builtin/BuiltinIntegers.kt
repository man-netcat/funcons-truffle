package builtin

import builtin.ValueNodeFactory.intNode
import com.oracle.truffle.api.frame.VirtualFrame
import generated.*
import java.math.BigInteger

open class IntegersNode : GroundValuesNode(), IntegersInterface

fun TermNode.isInIntegers(): Boolean = this is IntegersNode

open class IntegersFromNode(@Child override var p0: TermNode) : IntegersNode(), IntegersFromInterface
open class IntegersUpToNode(@Child override var p0: TermNode) : IntegersNode(), IntegersUpToInterface

// `integers` is "the type of unbounded integers" (Integers.cbs), so the
// representation has to be arbitrary-precision. Long overflowed silently,
// which made integer-power(2,64) saturate at Long.MAX_VALUE and made any
// literal above 2^63-1 fail to parse.
abstract class NumberNode(override val value: BigInteger) : IntegersNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is NumberNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString()
}

class NaturalNumberNode(override val value: BigInteger) : NumberNode(value) {
    constructor(value: Int) : this(BigInteger.valueOf(value.toLong()))
    constructor(value: Long) : this(BigInteger.valueOf(value))
}

class IntegerNode(override val value: BigInteger) : NumberNode(value) {
    constructor(value: Int) : this(BigInteger.valueOf(value.toLong()))
    constructor(value: Long) : this(BigInteger.valueOf(value))
}

private fun TermNode.asBigIntegerOrAbort(): BigInteger {
    if (!isInIntegers()) abort()
    return value as? BigInteger ?: abort()
}

private fun Array<out TermNode>.allIntegersOrAbort() {
    forEach { if (!it.isInIntegers()) it.abort() }
}

class IntegerAddNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), IntegerAddInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        p0.elements.allIntegersOrAbort()
        val sum = p0.elements.fold(BigInteger.ZERO) { acc, node -> acc + node.asBigIntegerOrAbort() }
        return intNode(sum)
    }
}

class IntegerSubtractNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerSubtractInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return intNode(p0.asBigIntegerOrAbort() - p1.asBigIntegerOrAbort())
    }
}

class IntegerMultiplyNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(),
    IntegerMultiplyInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        p0.elements.allIntegersOrAbort()
        val product = p0.elements.fold(BigInteger.ONE) { acc, node -> acc * node.asBigIntegerOrAbort() }
        return intNode(product)
    }
}

class IntegerDivideNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerDivideInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val dividend = p0.asBigIntegerOrAbort()
        val divisor = p1.asBigIntegerOrAbort()
        if (divisor.signum() == 0) return SequenceNode()
        return intNode(dividend / divisor)
    }
}

class IntegerModuloNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerModuloInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val dividend = p0.asBigIntegerOrAbort()
        val divisor = p1.asBigIntegerOrAbort()
        if (divisor.signum() == 0) return SequenceNode()
        return intNode(dividend % divisor)
    }
}

class IntegerPowerNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerPowerInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val base = p0.asBigIntegerOrAbort()
        val exponent = p1.asBigIntegerOrAbort()
        if (exponent.signum() < 0) abort()
        if (exponent.bitLength() > 31) abort()
        return intNode(base.pow(exponent.toInt()))
    }
}

class IntegerAbsoluteValueNode(@Eager @Child override var p0: TermNode) : TermNode(), IntegerAbsoluteValueInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return intNode(p0.asBigIntegerOrAbort().abs())
    }
}

class IntegerIsLessNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) : TermNode(),
    IntegerIsLessInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return if (p0.asBigIntegerOrAbort() < p1.asBigIntegerOrAbort()) TrueNode() else FalseNode()
    }
}

class IntegerIsGreaterNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsGreaterInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return if (p0.asBigIntegerOrAbort() > p1.asBigIntegerOrAbort()) TrueNode() else FalseNode()
    }
}

class IntegerIsLessOrEqualNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsLessOrEqualInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return if (p0.asBigIntegerOrAbort() <= p1.asBigIntegerOrAbort()) TrueNode() else FalseNode()
    }
}

class IntegerIsGreaterOrEqualNode(@Eager @Child override var p0: TermNode, @Eager @Child override var p1: TermNode) :
    TermNode(),
    IntegerIsGreaterOrEqualInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return if (p0.asBigIntegerOrAbort() >= p1.asBigIntegerOrAbort()) TrueNode() else FalseNode()
    }
}

class NaturalSuccessorNode(@Eager @Child override var p0: TermNode) : TermNode(), NaturalSuccessorInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return intNode(p0.asBigIntegerOrAbort() + BigInteger.ONE)
    }
}

class NaturalPredecessorNode(@Eager @Child override var p0: TermNode) : TermNode(), NaturalPredecessorInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val value = p0.asBigIntegerOrAbort()
        return if (value.signum() == 0) SequenceNode() else intNode(value - BigInteger.ONE)
    }
}

abstract class BaseConversionNode(
    @Eager @Child var p0: TermNode,
    val base: Int,
) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val numberStr = p0.asBigIntegerOrAbort().toString(base)
        val digits = numberStr.map { intNode(BigInteger.valueOf(it.digitToInt().toLong())) }
        return SequenceNode(*digits.toTypedArray())
    }
}

class BinaryNaturalNode(p0: TermNode) : BaseConversionNode(p0, 2), BinaryNaturalInterface
class OctalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 8), OctalNaturalInterface
class DecimalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 10), DecimalNaturalInterface
class HexadecimalNaturalNode(p0: TermNode) : BaseConversionNode(p0, 16), HexadecimalNaturalInterface
