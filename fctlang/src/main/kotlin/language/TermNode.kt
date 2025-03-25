package language

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import generated.*
import language.Util.DEBUG
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

@Suppress("UNCHECKED_CAST")
abstract class TermNode : Node() {
    enum class FrameSlots {
        LOCAL_CONTEXT
    }

    // Indices for parameters that are not lazily evaluated
    open val nonLazy = emptyList<Int>()
    val primaryConstructor = this::class.primaryConstructor!!
    val memberProperties = this::class.memberProperties
    val members = this::class.members
    val params: List<TermNode>
        get() {
            return primaryConstructor.parameters.mapNotNull { param ->
                memberProperties.first { it.name == param.name }.getter.call(this) as? TermNode
            }
        }
    val nonLazyParams: List<TermNode>
        get() = nonLazy.map { index -> params[index] }

    open val value: Any
        get() = when (this) {
            is FalseNode -> false
            is TrueNode -> true
            is NullValueNode -> "null-value"
            is FailedNode -> "failed"
            else -> this::class.simpleName!!
        }

    private fun getLanguage(): FCTLanguage {
        return FCTLanguage.get(this)
    }

    private fun getContext(): FCTContext {
        return FCTContext.get(this)!!
    }

    private fun getLocalContext(frame: VirtualFrame): MutableMap<String, Entity?> {
        return frame.getObject(FrameSlots.LOCAL_CONTEXT.ordinal) as? MutableMap<String, Entity?>
            ?: mutableMapOf<String, Entity?>().also {
                frame.setObject(FrameSlots.LOCAL_CONTEXT.ordinal, it)
            }
    }

    fun getInScope(frame: VirtualFrame, key: String): Entity? {
        return getLocalContext(frame)[key]
    }

    fun putInScope(frame: VirtualFrame, key: String, value: Entity?) {
        getLocalContext(frame)[key] = value
    }

    fun isInScope(frame: VirtualFrame, key: String): Boolean {
        return getLocalContext(frame).containsKey(key)
    }

    fun getGlobal(key: String): Entity? {
        return getContext().globalVariables.getEntity(key)
    }

    fun putGlobal(key: String, entity: Entity) {
        getContext().globalVariables.putEntity(key, entity)
    }

    fun appendGlobal(key: String, entity: StandardOutNode) {
        val existing = getContext().globalVariables.getEntity(key) as StandardOutNode?
        if (existing != null) {
            val newElements = existing.value.elements.toMutableList()
            newElements.addAll(entity.value.elements)
            val newSequence = SequenceNode(*newElements.toTypedArray())
            getContext().globalVariables.putEntity(key, StandardOutNode(newSequence))
        } else {
            getContext().globalVariables.putEntity(key, entity)
        }
    }

    open fun isReducible(): Boolean = when (this) {
        is SequenceNode -> elements.any { it !is ValuesNode }
        !is ValuesNode -> true
        else -> false
    }

    internal open fun reduce(frame: VirtualFrame): TermNode {
        reduceComputations(frame)?.let { new -> return replace(new) }
        return reduceRules(frame)
    }

    abstract fun reduceRules(frame: VirtualFrame): TermNode

    open fun reduceComputations(frame: VirtualFrame): TermNode? {
        val newParams = params.toMutableList()
        var attemptedReduction = false

        for (index in nonLazy) {
            if (newParams[index].isReducible()) {
                if (DEBUG) println("attempting to reduce ${newParams[index]::class.simpleName} in ${this::class.simpleName}")
                try {
                    attemptedReduction = true
                    newParams[index] = newParams[index].reduce(frame)
                    val new = primaryConstructor.call(*newParams.toTypedArray())
                    return replace(new)
                } catch (e: Exception) {
                    if (DEBUG) println("stuck in ${this::class.simpleName} with error $e")
                }
            }
        }

        return if (!attemptedReduction) null
        else throw IllegalStateException("All reductions failed")
    }

    fun instanceOf(other: ValueTypesNode): Boolean {
        return other::class.isInstance(this)
    }

    fun toSequence(): SequenceNode {
        return this as? SequenceNode ?: SequenceNode(this)
    }

    fun abort(reason: String = ""): Nothing = throw RuntimeException(reason)

    fun printTree(indent: String = "") {
        val value = if (this is ValuesNode) ": ${this.value}" else ""
        println("$indent${this::class.simpleName}$value")

        primaryConstructor.parameters.forEach { param ->
            val member = members.firstOrNull { it.name == param.name }
            val res = member!!.call(this)
            when (res) {
                is Array<*> -> res.forEach { (it as TermNode).printTree("$indent  ") }
                is TermNode -> res.printTree("$indent  ")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is Node -> false
            this is FalseNode && other is FalseNode -> true
            this is TrueNode && other is TrueNode -> true
            else -> false
        }
    }

    override fun hashCode(): Int {
        return this.value.hashCode()
    }

    override fun onReplace(newNode: Node?, reason: CharSequence?) {
        if (DEBUG) {
            val reasonStr = if (!reason.isNullOrEmpty()) " with reason: $reason" else ""
            if (newNode != null)
                println("replacing: ${this::class.simpleName} for ${newNode::class.simpleName}$reasonStr")
            else
                println("newNode is null $reasonStr")
        }
    }
}
