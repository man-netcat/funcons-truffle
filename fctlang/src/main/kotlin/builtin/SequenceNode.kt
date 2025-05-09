package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.FailNode
import generated.NullValueNode
import language.Util.DEBUG

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

    override fun sliceUntil(endIndex: Int, startIndexOffset: Int): SequenceNode {
        val adjustedEndIndex = if (endIndex == 0) size else size - endIndex
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

    override fun random(): TermNode {
        require(elements.isNotEmpty())
        return elements.random()
    }

    override fun shuffled(): List<TermNode> {
        return elements.toList().shuffled()
    }

    override fun reduceComputations(frame: VirtualFrame): TermNode? {
        val newElements = elements.toMutableList()

        for (index in elements.indices) {
            if (newElements[index].isReducible()) {
                try {
                    newElements[index] = newElements[index].reduce(frame)
                    return SequenceNode(*newElements.toTypedArray())
                } catch (e: Exception) {
                    if (DEBUG) println("Stuck with exception $e")
                }
            }
        }
        return null
    }

    override fun reduceRules(frame: VirtualFrame): TermNode = abort("sequence")

    override fun toString(): String = if (elements.isNotEmpty()) {
        elements.joinToString(",") { it.toString() }
    } else "()"

    override fun append(other: SequenceNode): SequenceNode {
        val newElements = mutableListOf<TermNode>()
        newElements.addAll(elements)
        newElements.addAll(other.elements)
        return SequenceNode(*newElements.toTypedArray())
    }

    override fun popFirst(): TermNode {
        if (elements.isEmpty()) return NullValueNode()
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

    override fun getEntity(frame: VirtualFrame, key: String): TermNode =
        abort("Invalid invocation on a sequence: getEntity")

    override fun putEntity(frame: VirtualFrame, key: String, value: TermNode) =
        abort("Invalid invocation on a sequence: putEntity")

    override fun appendEntity(frame: VirtualFrame, key: String, entity: TermNode) =
        abort("Invalid invocation on a sequence: appendEntity")
}