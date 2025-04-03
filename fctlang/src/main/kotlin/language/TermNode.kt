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
    open val eager = emptyList<Int>()
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
        get() = eager.map { index -> params[index] }

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

    fun appendGlobal(key: String, entity: Entity) {
        val existing = getContext().globalVariables.getEntity(key) as StandardOutNode?
        if (existing != null) {
            val newElements = existing.value.elements.toMutableList()
            newElements.addAll(entity.value.elements)
            val newSequence = SequenceNode(*newElements.toTypedArray())
            getContext().globalVariables.putEntity(key, StandardOutNode(newSequence))
        } else putGlobal(key, entity)
    }

    open fun isReducible(): Boolean = when (this) {
        is SequenceNode -> if (elements.isNotEmpty()) {
            elements.any { it !is ValuesNode }
        } else false

        !is ValuesNode -> true
        else -> nonLazyParams.any { it.isReducible() }
    }

    internal open fun reduce(frame: VirtualFrame): TermNode {
        reduceComputations(frame)?.let { new -> return replace(new) }
        return reduceRules(frame)
    }

    abstract fun reduceRules(frame: VirtualFrame): TermNode

    open fun reduceComputations(frame: VirtualFrame): TermNode? {
        val newParams = params.toMutableList()
        var attemptedReduction = false
        for (index in eager) {
            val currentParam = newParams[index]
            if (!currentParam.isReducible()) continue

            try {
                attemptedReduction = true
                newParams[index] = currentParam.reduce(frame)
                return primaryConstructor.call(*newParams.toTypedArray())
            } catch (e: Exception) {
            }
        }
        return null
//        return if (!attemptedReduction) null
//        else throw IllegalStateException("All reductions failed")
    }

    open fun isInType(type: TermNode): Boolean {
        if (this::class == type::class) return true
        if (type::class == ValuesNode::class) return true
        if (type::class == NullTypeNode::class) return true
        return when (type) {
            is UnionTypeNode -> type.types.any { this.isInType(it) }
            is IntersectionTypeNode -> type.types.all { this.isInType(it) }
            is ComplementTypeNode -> !this.isInType(type.type)
            is NullTypeNode -> false
            is NaturalNumbersNode -> this is NaturalNumberNode || (this is IntegerNode && value >= 0)
            is IntegersNode -> this is NaturalNumberNode || this is IntegerNode
            is BooleansNode -> this is FalseNode || this is TrueNode
            else -> type::class.isInstance(this)
        }
    }

    fun toSequence(): SequenceNode = this as? SequenceNode ?: SequenceNode(this)

    fun abort(reason: String = ""): Nothing = throw StuckException(reason)

    fun printTree(indent: String = "", prefix: String = "", hasMoreSiblings: Boolean = false) {
        val value = if (this is ValuesNode) ": ${this.value}" else ""
        println("$indent$prefix${this::class.simpleName}$value")

        val children = primaryConstructor.parameters.mapNotNull { param ->
            members.firstOrNull { it.name == param.name }?.call(this)
        }.flatMap {
            when (it) {
                is Array<*> -> it.toList()
                is TermNode -> listOf(it)
                else -> emptyList()
            }
        }

        for ((index, child) in children.withIndex()) {
            val isLast = index == children.lastIndex
            val newIndent = indent + if (hasMoreSiblings) "│   " else "    "
            (child as TermNode).printTree(newIndent, if (isLast) "└── " else "├── ", hasMoreSiblings = !isLast)
        }
    }


    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is TermNode) return false
        if (this::class != other::class) return false

        val thisParams = this.params
        val otherParams = other.params

        if (thisParams.size != otherParams.size) return false

        return thisParams.zip(otherParams).all { (a, b) -> a == b }
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

    fun rewrite(frame: VirtualFrame) {
        var term = this
        try {
            var iterationCount = 0
            while (term.isReducible()) {
                if (DEBUG) {
                    println("------------------")
                    println("Iteration $iterationCount: Current result is ${term::class.simpleName}")
                    term.printTree()
                }
                //            println(rootExpr.getContext().globalVariables)
                term = term.reduce(frame)
                iterationCount++
                if (iterationCount > 1000) throw InfiniteLoopException()
            }
        } catch (e: StuckException) {
            println("Failed to properly reduce.")
        } catch (e: InfiniteLoopException) {
            println("Infinite loop detected")
        }
    }
}
