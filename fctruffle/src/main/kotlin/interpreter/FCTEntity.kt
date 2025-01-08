package interpreter

open class FCTEntity(open val p0: FCTNode?) {
    val name: String
        get() = this::class.simpleName!!

    val isContextual = this is FCTContextualEntity
    val isControl = this is FCTControlEntity
    val isMutable = this is FCTMutableEntity

    val value: Any?
        get() = p0

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return p0?.hashCode() ?: 0
    }
}

open class FCTContextualEntity(value: FCTNode?) : FCTEntity(value)
open class FCTControlEntity(value: FCTNode?) : FCTEntity(value)
open class FCTMutableEntity(value: FCTNode?) : FCTEntity(value)
