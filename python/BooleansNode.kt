package fctruffle.generated

import fctruffle.main.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.NodeInfo

@NodeInfo(shortName = "booleans")
class BooleansNode(override val value: String) : Terminal() {
    override fun execute(frame: VirtualFrame): String = value
}

@NodeInfo(shortName = "not")
class NotNode(@Child private var p0: FCTNode) : Computation() {
    override fun execute(frame: VirtualFrame): FCTNode {
        if (p0.isTerminal()) {
            if (p0.execute(frame) == "false") {
                return BooleansNode("true")
            } else if (p0.execute(frame) == "true") {
                return BooleansNode("false")
            }
        } else if (p0.isComputation()) {
            return NotNode(p0.execute(frame) as FCTNode).execute(frame)
        }
        fail()
    }
}

@NodeInfo(shortName = "implies")
class ImpliesNode(@Child private var p0: FCTNode, @Child private var p1: FCTNode) : Computation() {
    override fun execute(frame: VirtualFrame): FCTNode {
        if (p0.isTerminal() && p1.isTerminal()) {
            if (p0.execute(frame) == "false" && p1.execute(frame) == "false") {
                return BooleansNode("true")
            } else if (p0.execute(frame) == "false" && p1.execute(frame) == "true") {
                return p1
            } else if (p0.execute(frame) == "true" && p1.execute(frame) == "true") {
                return p0
            } else if (p0.execute(frame) == "true" && p1.execute(frame) == "false") {
                return p1
            }
        } else if (p0.isComputation()) {
            return ImpliesNode(p0.execute(frame) as FCTNode, p1).execute(frame)
        } else if (p1.isComputation()) {
            return ImpliesNode(p0, p1.execute(frame) as FCTNode).execute(frame)
        }
        fail()
    }
}

@NodeInfo(shortName = "and")
class AndNode(
    @Children private vararg val p0: FCTNode
) : Computation() {
    override fun execute(frame: VirtualFrame): FCTNode {
        if (p0.isEmpty()) {
            return BooleansNode("true")
        } else if (p0[0].isTerminal()) {
            if (p0[0].execute(frame) == "false") {
                return p0[0]
            } else if (p0[0].execute(frame) == "true") {
                return AndNode(*slice(p0, 1)).execute(frame)
            }
        } else if (p0[0].isComputation()) {
            return AndNode(p0[0].execute(frame) as FCTNode, *slice(p0, 1)).execute(frame)
        }
        fail()
    }
}

@NodeInfo(shortName = "or")
class OrNode(
    @Children private vararg val p0: FCTNode
) : Computation() {
    override fun execute(frame: VirtualFrame): FCTNode {
        if (p0.isEmpty()) {
            return BooleansNode("false")
        } else if (p0[0].isTerminal()) {
            if (p0[0].execute(frame) == "true") {
                return p0[0]
            } else if (p0[0].execute(frame) == "false") {
                return OrNode(*slice(p0, 1)).execute(frame)
            }
        } else if (p0[0].isComputation()) {
            return OrNode(p0[0].execute(frame) as FCTNode, *slice(p0, 1)).execute(frame)
        }
        fail()
    }
}

@NodeInfo(shortName = "exclusive-or")
class ExclusiveOrNode(
    @Child private var p0: FCTNode, @Child private var p1: FCTNode
) : Computation() {
    override fun execute(frame: VirtualFrame): FCTNode {
        if (p0.isTerminal() && p1.isTerminal()) {
            if (p0.execute(frame) == "false" && p1.execute(frame) == "false") {
                return p0
            } else if (p0.execute(frame) == "false" && p1.execute(frame) == "true") {
                return p1
            } else if (p0.execute(frame) == "true" && p1.execute(frame) == "false") {
                return p0
            } else if (p0.execute(frame) == "true" && p1.execute(frame) == "true") {
                return BooleansNode("false")
            }
        } else if (p0.isComputation()) {
            return ExclusiveOrNode(p0.execute(frame) as FCTNode, p1).execute(frame)
        } else if (p1.isComputation()) {
            return ExclusiveOrNode(p0, p1.execute(frame) as FCTNode).execute(frame)
        }
        fail()
    }
}