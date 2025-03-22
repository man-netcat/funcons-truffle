package language

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import generated.FalseNode
import generated.NullValueNode
import generated.StandardOutNode
import generated.TrueNode
import language.Util.DEBUG
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

@Suppress("UNCHECKED_CAST")
abstract class TermNode : Node() {
    enum class FrameSlots {
        LOCAL_CONTEXT
    }

    // Indices for parameters that are not lazily evaluated
    open val reducibles = emptyList<Int>()

    open val value: Any
        get() = when (this) {
            is FalseNode -> false
            is TrueNode -> true
            is NullValueNode -> "null-value"
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

    // Scope Management
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

    // Reduction logic
    internal fun reduce(frame: VirtualFrame): TermNode {
        reduceComputations(frame)?.let { new -> return replace(new) }
        return reduceRules(frame)
    }

    abstract fun reduceRules(frame: VirtualFrame): TermNode

    fun isReducible(): Boolean {
        return when (this) {
            is SequenceNode -> elements.any { it.isReducible() }
            else -> this !is ValuesNode
        }
    }

    fun reduceComputations(frame: VirtualFrame): TermNode? {
        val primaryConstructor = this::class.primaryConstructor!!
        val memberProperties = this::class.memberProperties
        val params = primaryConstructor.parameters.mapNotNull { param ->
            memberProperties.first { it.name == param.name }.getter.call(this) as? TermNode
        }

        val newParams = params.toMutableList()
        var lastException: Exception? = null
        var attemptedReduction = false

        reducibles.forEach { index ->
            if (index in newParams.indices && newParams[index].isReducible()) {
                attemptedReduction = true
                try {
                    newParams[index] = newParams[index].reduce(frame)
                    return primaryConstructor.call(*newParams.toTypedArray())
                } catch (e: Exception) {
                    lastException = e
                }
            }
        }

        return if (attemptedReduction) {
            throw lastException ?: IllegalStateException("All reductions failed")
        } else null
    }

    fun instanceOf(other: ValueTypesNode): Boolean {
        return other::class.isInstance(this)
    }

    fun toSequence(): SequenceNode {
        return this as? SequenceNode ?: SequenceNode(this)
    }

    fun abort(reason: String = ""): Nothing = throw RuntimeException(reason)

    fun printTree(indent: String = "") {
        val value = if (this is ValuesNode) ": ${'$'}{this.value}" else ""
        println("${'$'}indent${'$'}{this::class.simpleName}${'$'}value")

        this::class.primaryConstructor!!.parameters.forEach { param ->
            val member = this::class.members.firstOrNull { it.name == param.name }
            val res = member!!.call(this)
            when (res) {
                is Array<*> -> res.forEach { (it as TermNode).printTree("${'$'}indent  ") }
                is TermNode -> res.printTree("${'$'}indent  ")
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
            val reasonStr = if (!reason.isNullOrEmpty()) " with reason: ${'$'}reason" else ""
            if (newNode != null)
                println("replacing: ${'$'}{this::class.simpleName} for ${'$'}{newNode::class.simpleName}${'$'}reasonStr")
            else
                println("newNode is null ${'$'}reasonStr")
        }
    }
}
