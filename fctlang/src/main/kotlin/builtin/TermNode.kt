package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import generated.*
import language.FCTLanguage
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
        ENTITIES,
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

    private fun getEntities(frame: VirtualFrame): MutableMap<String, TermNode?> {
        return frame.getObject(FrameSlots.ENTITIES.ordinal) as? MutableMap<String, TermNode?>
            ?: mutableMapOf<String, TermNode?>().also {
                frame.setObject(FrameSlots.ENTITIES.ordinal, it)
            }
    }

    internal fun printEntities(frame: VirtualFrame) {
        val entities = getEntities(frame)
        if (entities.isNotEmpty()) {
            val str = "Entities: {\n" + entities.map { (name, entity) -> "    $name: $entity" }
                .joinToString("\n") + "\n}"
            println(str)
        } else println("Entities: {}")
    }

    open fun getEntity(frame: VirtualFrame, key: String): TermNode {
        return getEntities(frame)[key] ?: SequenceNode()
    }

    open fun putEntity(frame: VirtualFrame, key: String, value: TermNode) {
        if (DEBUG) println("putting ${value::class.simpleName} ($value) in $key")
        getEntities(frame)[key] = value
        if (DEBUG) printEntities(frame)
    }

    open fun appendEntity(frame: VirtualFrame, key: String, entity: TermNode) {
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

    fun rewrite(frame: VirtualFrame): TermNode {
        if (DEBUG) println("rewriting: $this")
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
        }
        return term
    }

    internal fun reduce(frame: VirtualFrame): TermNode {
        if (DEBUG) println("reducing: ${this::class.simpleName}")
        // Reduce the parameters of a funcon first where possible
        reduceComputations(frame)?.let { new -> return replace(new) }
        // Reduce according to CBS semantic rules
        reduceRules(frame).let { new -> return replace(new) }
    }

    abstract fun reduceRules(frame: VirtualFrame): TermNode

    open fun reduceComputations(frame: VirtualFrame): TermNode? {
        var newParams = params.toMutableList()
        var attemptedReduction = false

        fun unpackTupleElements(i: Int, currentParam: TupleElementsNode): List<TermNode> {
            val tupleElements = (currentParam.p0 as ValueTupleNode).get(0).elements.toList()

            // Replace the tuple-elements node for its contents in-place in the parameter list
            newParams.removeAt(i)
            newParams.addAll(i, tupleElements)

            val sequenceIndex = primaryConstructor.parameters.indexOfFirst {
                it.type.classifier == SequenceNode::class
            }

            // If the original node expects a sequence, rebuild the parameter sequence to accomodate for this
            if (sequenceIndex != -1) {
                val beforeSequence = newParams.take(sequenceIndex)
                val afterSequence = newParams.drop(sequenceIndex).toTypedArray()
                newParams = (beforeSequence + SequenceNode(*afterSequence)) as MutableList<TermNode>
            }

            return newParams
        }

        for ((i, param) in primaryConstructor.parameters.withIndex()) {
            try {
                val currentParam = newParams[i]

                // We assume tuple-eleemnts is always reducible.
                if (
                    currentParam !is TupleElementsNode &&
                    (param.findAnnotation<Eager>() == null || !currentParam.isReducible())
                ) continue

                if (currentParam is TupleElementsNode && currentParam.p0 is ValueTupleNode) {
                    // In the case this is a fully reduced tuple-elements node, we must unpack it
                    unpackTupleElements(i, currentParam)
                } else {
                    attemptedReduction = true
                    newParams[i] = currentParam.reduce(frame)
                }

                val reconstructed = primaryConstructor.call(*newParams.toTypedArray())
                return reconstructed

            } catch (e: Exception) {
                if (DEBUG) println("Stuck with exception $e")
            }
        }

        return if (!attemptedReduction) null else abort("stuck!")
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
        println("$indent$prefix${this::class.simpleName}" + if (value == null) "" else " ($this)")

        val children = primaryConstructor.parameters.mapNotNull { param ->
            members.firstOrNull { it.name == param.name }?.call(this)
        }.flatMap {
            when (it) {
                is Array<*> -> it.toList()
                is SequenceNode -> it.children
                is TermNode -> listOf(it)
                else -> listOf()
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
        if (this is SequenceNode && other is SequenceNode) {
            return this.elements.zip(other.elements).all { (a, b) -> a == b }
        }

        val thisParams = this.params
        val otherParams = other.params

        if (thisParams.size != otherParams.size) return false

        return thisParams.zip(otherParams).all { (a, b) -> a == b }
    }

    override fun onReplace(newNode: Node?, reason: CharSequence?) {
        newNode as TermNode
        if (DEBUG) {
            val reasonStr = if (!reason.isNullOrEmpty()) " with reason: $reason" else ""
            println("replacing: ${this::class.simpleName} for ${newNode::class.simpleName}$reasonStr")
        }
    }

    open operator fun get(index: Int): TermNode {
        return try {
            params[index]
        } catch (e: IndexOutOfBoundsException) {
            FailNode()
        }
    }

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

    var copyCounter = 0
    open fun getCopy(index: Int): TermNode {
        return if (get(index).copyCounter == 0) {
            get(index).copyCounter++
            get(index)
        } else {
            get(index).deepCopy()
        }
    }

    override fun deepCopy(): TermNode {
        if (DEBUG) println("deepcopying ${this::class.simpleName}")
        if (!isReducible()) return this
        val constructor = primaryConstructor
        val args = params.map { param ->
            if (param.isReducible()) param.deepCopy() else param
        }.toTypedArray()

        return constructor.call(*args)
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
    open fun unpack(): Array<out TermNode> = abort("not a sequence: ${this::class.simpleName}")
    open fun slice(startIndex: Int, endIndex: Int): SequenceNode = abort("not a sequence: ${this::class.simpleName}")
    open fun sliceFrom(startIndex: Int, endIndexOffset: Int = 0): SequenceNode =
        abort("not a sequence: ${this::class.simpleName}")

    open fun sliceUntil(endIndexOffset: Int, startIndexOffset: Int = 0): SequenceNode =
        abort("not a sequence: ${this::class.simpleName}")

    open fun random(): TermNode = abort("not a sequence: ${this::class.simpleName}")
    open fun shuffled(): List<TermNode> = abort("not a sequence: ${this::class.simpleName}")
    open fun append(other: SequenceNode): SequenceNode = abort("not a sequence: ${this::class.simpleName}")
    open fun popFirst(): TermNode = abort("not a sequence: ${this::class.simpleName}")
}