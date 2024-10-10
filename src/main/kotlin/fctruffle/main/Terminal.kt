package fctruffle.main

abstract class Terminal(open val value: String) : FCTNode() {
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