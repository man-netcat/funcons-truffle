package language

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import generated.*

@Suppress("UNCHECKED_CAST")
abstract class FCTNode : Node() {
    abstract fun execute(frame: VirtualFrame): FCTNode

    private fun getLanguage(): FCTLanguage {
        return FCTLanguage.get(this)
    }

    private fun getContext(): FCTContext {
        return FCTContext.get(this)
    }

    private fun getLocalContext(frame: VirtualFrame): MutableMap<String, Entity?> {
        return frame.getObject(FrameSlots.LOCAL_CONTEXT.ordinal) as? MutableMap<String, Entity?>
            ?: mutableMapOf<String, Entity?>().also {
                frame.setObject(FrameSlots.LOCAL_CONTEXT.ordinal, it)
            }
    }

    protected fun getInScope(frame: VirtualFrame, key: String): Entity? {
        return getLocalContext(frame)[key]
    }

    protected fun putInScope(frame: VirtualFrame, key: String, value: Entity?) {
        getLocalContext(frame)[key] = value
    }

    protected fun isInScope(frame: VirtualFrame, key: String): Boolean {
        return getLocalContext(frame).containsKey(key)
    }

    protected fun getGlobal(key: String): Entity? {
        return getContext().globalVariables[key]
    }

    protected fun putGlobal(key: String, value: Entity) {
        getContext().globalVariables[key] = value
    }

    protected fun isGlobal(key: String): Boolean {
        return getContext().globalVariables.containsKey(key)
    }

    open val value: Any
        get() {
            if (this !is ValuesNode) throw IllegalStateException("Node with type: ${this::class.simpleName} is not of type ValuesNode")
            return when (this) {
                is FalseNode -> false
                is TrueNode -> true
                is NullValueNode -> "null-value"
                else -> throw IllegalStateException("Unsupported node type: ${this::class.simpleName}")
            }
        }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is Node -> false
            this is FalseNode && other is FalseNode -> true
            this is TrueNode && other is TrueNode -> true
            else -> {
                println("Not equal; nodes are: ${this::class.simpleName}, ${other::class.simpleName}")
                false
            }
        }
    }

    override fun hashCode(): Int {
        return this.value.hashCode()
    }

    fun isInstance(other: ValueTypesNode): Boolean {
        return other::class.isInstance(this)
    }

//    override fun onReplace(newNode: Node?, reason: CharSequence?) {
//        val reasonStr = if (reason != null && reason.isNotEmpty()) " with reason: $reason" else ""
//        if (newNode != null)
//            println("replacing: ${this::class.simpleName} for ${newNode::class.simpleName}$reasonStr")
//        else
//            println("newNode is null $reasonStr")
//    }
}

enum class FrameSlots {
    LOCAL_CONTEXT
}