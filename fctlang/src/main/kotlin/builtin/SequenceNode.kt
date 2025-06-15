package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.FailNode
import generated.TupleElementsNode
import language.StuckException
import language.getEntities
import language.restoreEntities

class SequenceNode(@Children override vararg var elements: TermNode) : TermNode() {
    init {
        elements = elements.flatMap {
            if (it is SequenceNode) it.elements.toList() else listOf(it)
        }.toTypedArray()
    }

    override val size: Int by lazy { elements.size }

    override fun slice(startIndex: Int, endIndex: Int): SequenceNode {
        val sliced = elements.sliceArray(startIndex until endIndex)
        return SequenceNode(*sliced)
    }

    override fun sliceFrom(startIndex: Int, endIndexOffset: Int): SequenceNode {
        val endIndex = size - endIndexOffset
        return slice(startIndex, endIndex)
    }

    override fun sliceUntil(endIndexOffset: Int, startIndexOffset: Int): SequenceNode {
        val adjustedEndIndex = if (endIndexOffset == 0) size else size - endIndexOffset
        val startIndex = startIndexOffset
        return slice(startIndex, adjustedEndIndex)
    }

    override val head: TermNode by lazy { get(0) }
    override val second: TermNode by lazy { get(1) }
    override val third: TermNode by lazy { get(2) }
    override val fourth: TermNode by lazy { get(3) }
    override val last: TermNode by lazy { get(elements.size - 1) }
    override val init: SequenceNode by lazy { sliceUntil(1) }
    override val tail: SequenceNode by lazy { sliceFrom(1) }
    override fun isEmpty(): Boolean = elements.isEmpty()
    override fun isNotEmpty(): Boolean = elements.isNotEmpty()
    override fun isReducible(): Boolean = elements.any { it !is ValuesNode }

    override fun random(): TermNode {
        require(elements.isNotEmpty())
        return elements.random()
    }

    override fun shuffled(): List<TermNode> {
        return elements.toList().shuffled()
    }

    private val reducibleElements
        get() = elements.mapIndexed { index, param -> index to param }
            .filter { it.second.isReducible() }

    override fun reduceComputations(frame: VirtualFrame): TermNode? {
        var newElements = elements.toMutableList()
        val entitySnapshot = getEntities(frame).toMap()

        if (reducibleElements.isEmpty()) return null

        for ((index, element) in reducibleElements) {
            try {
                if (element is TupleElementsNode && element.p0 is ValueTupleNode) {
                    // In the case this is a fully reduced tuple-elements node, we must unpack it
                    newElements = unpackTupleElements(index, element)
                } else {
                    newElements[index] = element.reduce(frame)
                }

                return replace(SequenceNode(*newElements.toTypedArray()))

            } catch (e: StuckException) {
                // Rollback entities
                restoreEntities(frame, entitySnapshot)
            }
        }

        abort("no execution possible")
    }

    override fun unpackTupleElements(
        index: Int,
        tupleElementsNode: TupleElementsNode,
    ): MutableList<TermNode> {
        val paramList = elements.toMutableList()
        val tupleElements = (tupleElementsNode.p0 as ValueTupleNode).get(0).elements.toList()

        paramList.removeAt(index)
        paramList.addAll(index, tupleElements)

        val truncated = paramList.dropLastWhile { it is SequenceNode && it.elements.isEmpty() }.toMutableList()
        return truncated
    }

    override fun reduceRules(frame: VirtualFrame): TermNode = abort()

    override fun toString(): String = if (elements.isNotEmpty()) {
        elements.joinToString { it.toString() }
    } else "()"

    override fun append(other: SequenceNode): SequenceNode {
        val newElements = mutableListOf<TermNode>()
        newElements.addAll(elements)
        newElements.addAll(other.elements)
        return SequenceNode(*newElements.toTypedArray())
    }

    override fun popFirst(): TermNode {
        if (elements.isEmpty()) return ValueNullValueNode()
        val firstElement = elements[0]
        elements = elements.sliceArray(1 until elements.size)
        return firstElement
    }

    override operator fun get(index: Int): TermNode {
        return elements.getOrNull(index) ?: FailNode()
    }

    override fun deepCopy(): TermNode {
        if (!isReducible()) return this
        val args = elements.map { it ->
            if (it.isReducible()) it.deepCopy() else it
        }.toTypedArray()
        return SequenceNode(*args)
    }

    override fun unpack(): Array<out TermNode> {
        return elements
    }

    override fun hashCode(): Int {
        return elements.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as SequenceNode

        return elements.contentEquals(other.elements)
    }

    fun toStringLiteral(): String? {
        return if (elements.all { it is CharacterNode }) {
            elements.joinToString("") { (it as CharacterNode).value.toString() }
        } else null
    }
}
