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

    val primaryCtor = this::class.primaryConstructor!!
    val props = this::class.memberProperties
    val members = this::class.members

    val params: List<TermNode> by lazy {
        primaryCtor.parameters.mapNotNull { param ->
            props.first { it.name == param.name }.getter.call(this) as? TermNode
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
        if (DEBUG) println("reducing: ${this::class.simpleName} with params ${params.map { it::class.simpleName }}")
        // Reduce the parameters of a funcon first where possible
        if (this !is DirectionalNode) reduceComputations(frame)?.let { new -> return replace(new) }
        // Reduce according to CBS semantic rules
        reduceRules(frame).let { new -> return replace(new) }
    }

    abstract fun reduceRules(frame: VirtualFrame): TermNode

    open fun reduceComputations(frame: VirtualFrame): TermNode? {
        var newParams = params.toMutableList()
        var attemptedReduction = false

        for ((index, kParam) in primaryCtor.parameters.withIndex()) {
            try {
                val currentParam = newParams[index]

                // We assume tuple-elements is always reducible.
                if (
                    currentParam !is TupleElementsNode &&
                    (kParam.findAnnotation<Eager>() == null || !currentParam.isReducible())
                ) continue

                if (currentParam is TupleElementsNode && currentParam.p0 is ValueTupleNode) {
                    // In the case this is a fully reduced tuple-elements node, we must unpack it
                    newParams = unpackTupleElements(index, currentParam, newParams)
                } else {
                    attemptedReduction = true
                    newParams[index] = currentParam.reduce(frame)
                }

                return primaryCtor.call(*newParams.toTypedArray())

            } catch (e: StuckException) {
                if (DEBUG) println("Stuck with exception $e in class ${this::class.simpleName}")
            }
        }

        return if (attemptedReduction) abort("stuck!") else null
    }

    private fun unpackTupleElements(
        index: Int,
        tupleElementsNode: TupleElementsNode,
        paramList: MutableList<TermNode>
    ): MutableList<TermNode> {
        if (DEBUG) println("unpacking tuple-elements with params ${tupleElementsNode.p0.elements.map { it::class.simpleName }}")
        val tupleElements = (tupleElementsNode.p0 as ValueTupleNode).get(0).elements.toList()

        // Replace the tuple-elements node for its contents in-place in the parameter list
        paramList.removeAt(index)
        paramList.addAll(index, tupleElements)

        val sequenceIndex = primaryCtor.parameters.indexOfFirst {
            it.type.classifier == SequenceNode::class
        }

        // If the original node expects a sequence, rebuild the parameter sequence to accommodate for this
        val newParams = if (sequenceIndex != -1) {
            val beforeSequence = paramList.take(sequenceIndex)
            val afterSequence = paramList.drop(sequenceIndex).toTypedArray()
            beforeSequence + SequenceNode(*afterSequence)
        } else paramList

        // Return truncated param list
        return newParams.take(params.size).toMutableList()
    }

    open fun isInType(type: TermNode): Boolean {
        return when {
            type::class == ValuesNode::class -> true
            type::class == EmptyTypeNode::class -> false
            type::class == UnionTypeNode::class -> (type as UnionTypeNode).types.any { this.isInType(it) }
            type::class == IntersectionTypeNode::class -> (type as IntersectionTypeNode).types.all { this.isInType(it) }
            type::class == ComplementTypeNode::class -> !(type as ComplementTypeNode).type.let { this.isInType(it) }
            type::class == GroundValuesNode::class -> this.isInGroundValues()
            type::class == NullTypeNode::class -> false
            type::class == NaturalNumbersNode::class -> this is NaturalNumberNode || (this is IntegerNode && value >= 0)
            type::class == IntegersNode::class -> this is NaturalNumberNode || this is IntegerNode
            type::class == BooleansNode::class -> this.isInBooleans()
            type::class == MapsNode::class -> {
                type as MapsNode
                this is ValueMapNode && this.vp0.elements.all { tuple ->
                    tuple as ValueTupleNode
                    val (tuple0, tuple1) = tuple.vp0.elements
                    tuple0.isInType(type.mapsTp0) && tuple1.isInType(type.mapsTp1)
                }
            }

            type::class == StoresNode::class -> {
                type as MapsNode
                this is ValueMapNode && this.vp0.elements.all { tuple ->
                    tuple as ValueTupleNode
                    val tuple0 = tuple.vp0.elements[0]
                    val tuple1 = tuple.vp0.elements.getOrElse(1) { SequenceNode() }
                    tuple0.isInType(LocationsNode()) && tuple1.isInType(UnionTypeNode(ValuesNode(), SequenceNode()))
                }
            }

            type::class == StringsNode::class -> {
                type as ListsNode
                this is ValueListNode && this.vp0.elements.all { element ->
                    element.isInType(CharactersNode())
                }
            }

            type::class == EnvironmentsNode::class -> this.isInEnvironments()
            type::class == LocationsNode::class -> this.isInLocations()
            type::class == AbstractionsNode::class -> this.isInAbstractions()
            type::class == AtomsNode::class -> this.isInAtoms()
            type::class == IdentifiersNode::class -> this.isInIdentifiers()
            type::class == LinksNode::class -> this.isInLinks()
            type::class == PatternsNode::class -> this.isInPatterns()
            type::class == ThunksNode::class -> this.isInThunks()
            type::class == VariantsNode::class -> this.isInVariants()
            type::class == RecordsNode::class -> this.isInRecords()
            type::class == ReferencesNode::class -> this.isInReferences()
            type::class == PointersNode::class -> this.isInPointers()
            type::class == ClassesNode::class -> this.isInClasses()
            type::class == ObjectsNode::class -> this.isInObjects()
            type::class == BitVectorsNode::class -> this.isInBitVectors()
            type::class == VectorsNode::class -> {
                type as VectorsNode
                this is ValueVectorNode && this.vp0.elements.all { element ->
                    element.isInType(type.vectorsTp0)
                }
            }

            type::class == ListsNode::class -> {
                type as ListsNode
                this is ValueListNode && this.vp0.elements.all { element ->
                    element.isInType(type.listsTp0)
                }
            }

            type::class == SetsNode::class -> {
                type as SetsNode
                this is ValueSetNode && this.vp0.elements.all { element ->
                    element.isInType(type.setsTp0)
                }
            }

            type::class == TuplesNode::class -> {
                type as TuplesNode
                this is ValueTupleNode && this.vp0.elements.zip(type.tuplesTp0.elements)
                    .all { (tupleElement, tupleType) ->
                        tupleElement.isInType(tupleType)
                    }
            }

            type::class == DatatypeValuesNode::class -> this.isInDatatypeValues()
            type::class == ValueTypesNode::class -> this.isInValueTypes()
            // TODO: Check if everything is here. May need to figure out way to automate this
            else -> throw IllegalStateException("Unexpected type: $type")
        }
    }

    fun toSequence(): SequenceNode = this as? SequenceNode ?: SequenceNode(this)

    fun abort(reason: String = ""): Nothing = throw StuckException(reason)

    fun printTree(indent: String = "", prefix: String = "", hasMoreSiblings: Boolean = false) {
        println("$indent$prefix${this::class.simpleName}" + if (value == null) "" else " ($this)")

        val children = primaryCtor.parameters.mapNotNull { param ->
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
        if (this is SequenceNode && other is SequenceNode) {
            return this.elements.zip(other.elements).all { (a, b) -> a == b }
        }

        if (this is AbstractDatatypeValueNode && other is AbstractDatatypeValueNode) {
            return this.id == other.id && this.args == other.args
        }

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
            println("replacing: ${this::class.simpleName} for ${newNode::class.simpleName}$reasonStr")
        }
    }

    open operator fun get(index: Int): TermNode = params.getOrNull(index) ?: FailNode()

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
        val constructor = primaryCtor
        val args = params.map { param ->
            if (param.isReducible()) param.deepCopy() else param
        }.toTypedArray()

        return constructor.call(*args)
    }

    open val id: TermNode get() = FailNode()
    open val args: TermNode get() = FailNode()
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