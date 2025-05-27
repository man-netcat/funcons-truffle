package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class FloatsNode(override val p0: TermNode) : GroundValuesNode(), FloatsInterface

fun TermNode.isInFloats(): Boolean = this is FloatsNode

data class FloatNode(
    override val p0: TermNode,
    override val p1: TermNode,
    override val p2: TermNode,
    override val p3: TermNode,
) : FloatsNode(FloatFormatsNode()), FloatInterface {
    override fun toString(): String {
        return "float($p0, $p1, $p2, $p3)"
    }
}

class QuietNotANumberNode(override val p0: TermNode) : TermNode(), QuietNotANumberInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return FloatNode(p0, IntegerNode(0), IntegerNode(0), IntegerNode(0))
    }
}

class SignalingNotANumberNode(override val p0: TermNode) : TermNode(), SignalingNotANumberInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return FloatNode(p0, IntegerNode(0), IntegerNode(1), IntegerNode(0))
    }
}

class PositiveInfinityNode(override val p0: TermNode) : TermNode(), PositiveInfinityInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return FloatNode(p0, IntegerNode(0), IntegerNode(0), IntegerNode(1))
    }
}

class NegativeInfinityNode(override val p0: TermNode) : TermNode(), NegativeInfinityInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return FloatNode(p0, IntegerNode(1), IntegerNode(0), IntegerNode(1))
    }
}
