package interpreter

open class FCTEntity(open val p0: Any?) {
    val name: String
        get() = this::class.simpleName!!

    val isContextual = this is FCTContextualEntity
    val isControl = this is FCTControlEntity
    val isMutable = this is FCTMutableEntity

    val value: Any?
        get() = p0
}

open class FCTContextualEntity(value: Any?) : FCTEntity(value)
open class FCTControlEntity(value: Any?) : FCTEntity(value)
open class FCTMutableEntity(value: Any?) : FCTEntity(value)
