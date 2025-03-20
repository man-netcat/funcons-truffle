package language

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import generated.FalseNode
import generated.NullValueNode
import generated.StandardOutNode
import generated.TrueNode
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

@Suppress("UNCHECKED_CAST")
abstract class TermNode : Node() {
    enum class FrameSlots {
        LOCAL_CONTEXT
    }

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

    private fun getContext(): FCTContext {
        return FCTContext.get(this)!!
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
            val newElements = mutableListOf<TermNode>()
            newElements.addAll(existing.value.elements)
            newElements.addAll(entity.value.elements)
            val newSequence = SequenceNode(*newElements.toTypedArray())
            getContext().globalVariables.putEntity(key, StandardOutNode(newSequence))
        } else getContext().globalVariables.putEntity(key, entity)
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

    fun toSequence(): SequenceNode {
        return this as? SequenceNode ?: SequenceNode(this)
    }

    fun isReducible(): Boolean {
        return when (this) {
            is SequenceNode -> elements.any { it.isReducible() }
            else -> this !is ValuesNode
        }
    }

    fun reduceComputations(frame: VirtualFrame, reducibleIndices: List<Int>): TermNode? {
        // Obtain terms using Kotlin Reflect's wacky voodoo magic
        val primaryConstructor = this::class.primaryConstructor!!
        val memberProperties = this::class.memberProperties
        val params = primaryConstructor.parameters.mapNotNull { param ->
            memberProperties.first() { it.name == param.name }.getter.call(this)!!.let { it as? TermNode }
        }

        val newParams = params.toMutableList()
        var lastException: Exception? = null
        var attemptedReduction = false

        reducibleIndices.forEach { index ->
            if (index in newParams.indices && newParams[index].isReducible()) {
                attemptedReduction = true
                try {
                    newParams[index] = newParams[index].reduce(frame)
                    // Return on first success
                    return primaryConstructor.call(*newParams.toTypedArray())
                } catch (e: Exception) {
                    lastException = e
                }
            }
        }

        // If no reduction was even attempted (Thus all terms are irreducible), return null instead of throwing
        // If least one reduction has been attempted, it means we were unable to reduce, and thus we throw an exception
        return if (attemptedReduction) {
            throw lastException ?: IllegalStateException("All reductions failed")
        } else null
    }
}