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
    abstract val value: String

    abstract override fun execute(frame: VirtualFrame): String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return value == other
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "${this::class.simpleName}($value)"
    }
}
