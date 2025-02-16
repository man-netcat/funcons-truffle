package interpreter

abstract class FCTEntity(open val p0: FCTNode?) {
    val value: Any?
        get() = p0

    override fun hashCode(): Int {
        return p0?.hashCode() ?: 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FCTEntity

        if (p0 != other.p0) return false
        if (typeName != other.typeName) return false

        return true
    }

    abstract val typeName: String
}

abstract class FCTContextualEntity(value: FCTNode?) : FCTEntity(value)
abstract class FCTControlEntity(value: FCTNode?) : FCTEntity(value)
abstract class FCTMutableEntity(value: FCTNode?) : FCTEntity(value)
