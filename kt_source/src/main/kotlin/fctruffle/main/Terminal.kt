package fctruffle.main

import com.oracle.truffle.api.frame.VirtualFrame

abstract class Terminal : FCTNode() {
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