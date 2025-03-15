package language

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import generated.*
import kotlin.reflect.full.primaryConstructor

@Suppress("UNCHECKED_CAST")
abstract class TermNode : Node() {
    abstract fun reduce(frame: VirtualFrame): TermNode

    private fun getLanguage(): FCTLanguage {
        return FCTLanguage.get(this)
    }

    private fun getLocalContext(frame: VirtualFrame): MutableMap<String, Entity?> {
        return frame.getObject(FrameSlots.LOCAL_CONTEXT.ordinal) as? MutableMap<String, Entity?>
            ?: mutableMapOf<String, Entity?>().also {
                frame.setObject(FrameSlots.LOCAL_CONTEXT.ordinal, it)
            }
    }

    fun getContext(): FCTContext {
        return FCTContext.get(this)!!
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
        return getContext().globalVariables.getEntity(key)
    }

    protected fun putGlobal(key: String, value: Entity) {
        getContext().globalVariables.putEntity(key, value)
    }

    open val value: Any
        get() {
            return when (this) {
                is FalseNode -> false
                is TrueNode -> true
                is NullValueNode -> "null-value"
                else -> this::class.simpleName!!
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

    fun instanceOf(other: ValueTypesNode): Boolean {
        return other::class.isInstance(this)
    }

//    override fun onReplace(newNode: Node?, reason: CharSequence?) {
//        val reasonStr = if (reason != null && reason.isNotEmpty()) " with reason: $reason" else ""
//        if (newNode != null)
//            println("replacing: ${this::class.simpleName} for ${newNode::class.simpleName}$reasonStr")
//        else
//            println("newNode is null $reasonStr")
//    }

    fun printTree(indent: String = "") {
        // For debug purposes only

        val value = if (this is ValuesNode) ": ${this.value}" else ""
        println("$indent${this::class.simpleName}$value")

        this::class.primaryConstructor!!.parameters.forEach { param ->
            val member = this::class.members.firstOrNull { it.name == param.name }
            val res = member!!.call(this)
            when (res) {
                is Array<*> -> res.forEach { (it as TermNode).printTree("$indent  ") }
                is TermNode -> res.printTree("$indent  ")
            }
        }
    }

    fun abort(reason: String = ""): Nothing = throw RuntimeException(reason)

    fun asSequence(): SequenceNode {
        return this as? SequenceNode ?: SequenceNode(this)
    }
}

enum class FrameSlots {
    LOCAL_CONTEXT
}
