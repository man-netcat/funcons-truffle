package fctruffle.main

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

abstract class FCTNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any

    fun isTerminal(): Boolean {
        return this is Terminal
    }

    fun isComputation(): Boolean {
        return this is Computation
    }
}