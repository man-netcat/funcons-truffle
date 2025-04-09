package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.NullValueNode
import language.Util.DEBUG

class SequenceNode(@Children override vararg var elements: TermNode) : TermNode() {
    init {
        elements = elements.flatMap {
            if (it is SequenceNode) it.elements.toList() else listOf(it)
        }.toTypedArray()
    }

    override val size: Int by lazy { elements.size }

    override fun sliceFrom(startIndex: Int, endIndexOffset: Int): SequenceNode {
        val endIndex = size - endIndexOffset
        require(endIndexOffset >= 0 && endIndex <= size && startIndex <= endIndex) {
            "Invalid end index offset."
        }

        val sliced = elements.sliceArray(startIndex until endIndex)
        return SequenceNode(*sliced)
    }

    override val head: TermNode by lazy { get(0) }
    override val second: TermNode by lazy { get(1) }
    override val third: TermNode by lazy { get(2) }
    override val fourth: TermNode by lazy { get(3) }
    override val tail: SequenceNode by lazy { sliceFrom(1) }

    fun random(): TermNode {
        require(elements.isNotEmpty())
        return elements.random()
    }

    override fun reduceComputations(frame: VirtualFrame): TermNode? {
        val newElements = elements.toMutableList()
        var attemptedReduction = false

        for (index in elements.indices) {
            if (newElements[index].isReducible()) {
                try {
                    attemptedReduction = true
                    newElements[index] = newElements[index].reduce(frame)
                    return SequenceNode(*newElements.toTypedArray())
                } catch (e: Exception) {
                    if (DEBUG) println("Stuck with exception $e")
                }
            }
        }
        return null
//        return if (!attemptedReduction) null
//        else throw IllegalStateException("All reductions failed")
    }

    override fun reduceRules(frame: VirtualFrame): TermNode = abort("sequence")

    override fun toString(): String {
        return elements.joinToString("") { it.value.toString() }
    }

    fun append(other: SequenceNode): SequenceNode {
        val newElements = mutableListOf<TermNode>()
        newElements.addAll(elements)
        newElements.addAll(other.elements)
        return SequenceNode(*newElements.toTypedArray())
    }

    override val value: Any by lazy { elements.joinToString { it.value.toString() } }

    fun popFirst(): TermNode {
        if (elements.isEmpty()) return NullValueNode()
        val firstElement = elements[0]
        elements = elements.sliceArray(1 until elements.size)
        return firstElement
    }

    override operator fun get(index: Int): TermNode {
        return try {
            elements[index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            SequenceNode()
        }
    }
}