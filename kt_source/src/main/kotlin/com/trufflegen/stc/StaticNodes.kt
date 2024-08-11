package com.trufflegen.stc

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

abstract class CBSNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any

    fun isTerminal(): Boolean {
        return this is Terminal
    }

    fun isComputation(): Boolean {
        return this is Computation
    }
}

abstract class Computation : CBSNode() {
    abstract override fun execute(frame: VirtualFrame): Any
}

abstract class Terminal : CBSNode() {
    abstract override fun execute(frame: VirtualFrame): String
}
