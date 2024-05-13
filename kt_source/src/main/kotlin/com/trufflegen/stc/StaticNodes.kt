package com.trufflegen.stc

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node


abstract class ExprNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any?
}