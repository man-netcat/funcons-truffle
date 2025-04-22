package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import generated.*
import language.FCTLanguage
import language.InfiniteLoopException
import language.StuckException
import language.Util.DEBUG
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

@Suppress("UNCHECKED_CAST")
abstract class TermNode : Node() {
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Eager

    enum class FrameSlots {
        LOCAL_CONTEXT
    }

    val primaryConstructor = this::class.primaryConstructor!!
    val memberProperties = this::class.memberProperties
    val members = this::class.members

    val params: List<TermNode> by lazy {
        primaryConstructor.parameters.mapNotNull { param ->
            memberProperties.first { it.name == param.name }.getter.call(this) as? TermNode
        }
    }

    val name: String
        get() {
            val nodeName = this::class.simpleName!!
            val base = nodeName.substring(0, nodeName.length - "Node".length)

            return base.mapIndexed { i, ch ->
                if (ch.isUpperCase()) {
                    val dash = if (i != 0) '-' else ""
                    "$dash${ch.lowercaseChar()}"
                } else ch
            }.joinToString("")
        }

    private fun getLanguage(): FCTLanguage {
        return FCTLanguage.Companion.get(this)
    }

    private fun getLocalContext(frame: VirtualFrame): MutableMap<String, TermNode?> {
        return frame.getObject(FrameSlots.LOCAL_CONTEXT.ordinal) as? MutableMap<String, TermNode?>
            ?: mutableMapOf<String, TermNode?>().also {
                frame.setObject(FrameSlots.LOCAL_CONTEXT.ordinal, it)
            }
    }

    internal fun printEntities(frame: VirtualFrame) {
        val context = getLocalContext(frame)
        if (context.isNotEmpty()) {
            val str = "Entities: {\n" + context.map { (name, entity) -> "    $name: $entity" }
                .joinToString("\n") + "\n}"
            println(str)
        } else println("{}")
    }

    fun getEntity(frame: VirtualFrame, key: String): TermNode {
        return getLocalContext(frame)[key] ?: SequenceNode()
    }

    fun putEntity(frame: VirtualFrame, key: String, value: TermNode) {
        if (DEBUG) println("putting ${value::class.simpleName} ($value) in $key")
        getLocalContext(frame)[key] = value
        if (DEBUG) printEntities(frame)
    }

    fun appendEntity(frame: VirtualFrame, key: String, entity: TermNode) {
        val existing = getEntity(frame, key) as? SequenceNode ?: SequenceNode()
        val newSequence = existing.append(entity.toSequence())
        putEntity(frame, key, newSequence)
    }

    open fun isReducible(): Boolean = when (this) {
        is SequenceNode -> if (elements.isNotEmpty()) {
            elements.any { it !is ValuesNode }
        } else false

        else -> this !is ValuesNode
    }

    internal open fun reduce(frame: VirtualFrame): TermNode {
        if (DEBUG) println("reducing: ${this::class.simpleName} ($this)")
        reduceComputations(frame)?.let { new -> return replace(new) }
        reduceRules(frame).let { new -> return replace(new) }
    }

    abstract fun reduceRules(frame: VirtualFrame): TermNode

    open fun reduceComputations(frame: VirtualFrame): TermNode? {
        val newParams = params.toMutableList()
        var attemptedReduction = false

        for ((i, param) in primaryConstructor.parameters.withIndex()) {
            if (param.findAnnotation<Eager>() == null) continue

            val currentParam = newParams[i]
            if (!currentParam.isReducible()) continue

            try {
                attemptedReduction = true
                newParams[i] = currentParam.reduce(frame)
                return primaryConstructor.call(*newParams.toTypedArray())
            } catch (e: Exception) {
                if (DEBUG) println("Stuck with exception $e")
            }
        }

        if (!attemptedReduction) return null
        else abort("stuck!")
    }

    open fun isInType(type: TermNode): Boolean {
        if (this::class == type::class) return true
        if (type::class == ValuesNode::class) return true
        if (type::class == NullTypeNode::class) return false

        return when (type) {
            is UnionTypeNode -> type.types.any { this.isInType(it) }
            is IntersectionTypeNode -> type.types.all { this.isInType(it) }
            is ComplementTypeNode -> !this.isInType(type.type)
            is NullTypeNode -> false
            is NaturalNumbersNode -> this is NaturalNumberNode || (this is IntegerNode && value >= 0)
            is IntegersNode -> this is NaturalNumberNode || this is IntegerNode
            is BooleansNode -> this is FalseNode || this is TrueNode
            is MapsNode -> this is ValueMapNode
            is StringsNode -> this is ValueListNode && this.p0.elements.all { it.isInCharacters() }
            is ListsNode -> this is ValueListNode
            is VectorsNode -> this is ValueVectorNode
            is AtomsNode -> this is AtomNode
            is IdentifiersNode -> (this is ValueIdentifierTaggedNode && this.p0.isInIdentifiers()) || this.isInType(
                StringsNode()
            )

            else -> type::class.isInstance(this)
        }
    }

    fun toSequence(): SequenceNode = this as? SequenceNode ?: SequenceNode(this)

    fun abort(reason: String = ""): Nothing = throw StuckException(reason)

    fun printTree(indent: String = "", prefix: String = "", hasMoreSiblings: Boolean = false) {
        val value = if (this is ValuesNode) ": $this" else ""
        println("$indent$prefix${this::class.simpleName}$value")

        val children = primaryConstructor.parameters.mapNotNull { param ->
            members.firstOrNull { it.name == param.name }?.call(this)
        }.flatMap {
            when (it) {
                is Array<*> -> it.toList()
                is SequenceNode -> it.children
                is TermNode -> listOf(it)
                else -> emptyList()
            }
        }

        children.forEachIndexed { index, child ->
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

    override fun onReplace(newNode: Node?, reason: CharSequence?) {
        newNode as TermNode
        if (DEBUG) {
            val reasonStr = if (!reason.isNullOrEmpty()) " with reason: $reason" else ""
            println("replacing: ${this::class.simpleName} for ${newNode::class.simpleName} ($newNode)$reasonStr")
        }
    }

    fun rewrite(frame: VirtualFrame): TermNode {
        var term = this
        var iterationCount = 0
        while (term.isReducible()) {
            if (DEBUG) {
                println("------------------")
                println("Iteration $iterationCount: Current result is ${term::class.simpleName}")
                term.printTree()
            }
            term = term.reduce(frame)
            iterationCount++
            if (iterationCount > 1000) throw InfiniteLoopException()
        }
        return term
    }

    open operator fun get(index: Int): TermNode {
        return try {
            params[index]
        } catch (e: IndexOutOfBoundsException) {
            FailNode()
        }
    }

    open val head: TermNode get() = abort("not a sequence: ${this::class.simpleName}")
    open val second: TermNode get() = abort("not a sequence: ${this::class.simpleName}")
    open val third: TermNode get() = abort("not a sequence: ${this::class.simpleName}")
    open val fourth: TermNode get() = abort("not a sequence: ${this::class.simpleName}")
    open val last: TermNode get() = abort("not a sequence: ${this::class.simpleName}")
    open val tail: SequenceNode get() = abort("not a sequence: ${this::class.simpleName}")
    open val init: SequenceNode get() = abort("not a sequence: ${this::class.simpleName}")
    open val size: Int get() = abort("not a sequence: ${this::class.simpleName}")
    open val elements: Array<out TermNode> get() = abort("not a sequence: ${this::class.simpleName}")
    open fun isEmpty(): Boolean = false
    open fun isNotEmpty(): Boolean = true
    open fun sliceFrom(startIndex: Int, endIndexOffset: Int = 0): SequenceNode =
        abort("not a sequence: ${this::class.simpleName}")

    open fun sliceUntil(endIndexOffset: Int, startIndexOffset: Int = 0): SequenceNode =
        abort("not a sequence: ${this::class.simpleName}")

    open val value: Any? get() = null

    override fun toString(): String {
        return name + if (params.isNotEmpty()) "(" + params.joinToString(",") { it.toString() } + ")" else ""
    }

    override fun hashCode(): Int {
        var result = this::class.hashCode()
        result = 31 * result + params.hashCode()
        return result
    }

    fun printWithClassName() {
        println("$this: ${this::class.simpleName}")
    }
}