package com.trufflegen.stc

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

abstract class CBSNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any
}

abstract class DataTypeNode : CBSNode() {
    abstract override fun execute(frame: VirtualFrame): String
}

abstract class FunconNode : CBSNode() {
    abstract override fun execute(frame: VirtualFrame): Any
}
