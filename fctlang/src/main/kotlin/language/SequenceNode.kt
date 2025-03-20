package language

import com.oracle.truffle.api.frame.VirtualFrame

@Builtin
open class SequenceNode(@Children vararg var elements: TermNode) : TermNode() {
    init {
        elements = elements.flatMap {
            if (it is SequenceNode) it.elements.toList() else listOf(it)
        }.toTypedArray()
    }

    fun reduceReverse(frame: VirtualFrame): SequenceNode {
        val i = elements.indexOfLast { it !is ValuesNode }
        val newElements = elements.mapIndexed { index, node ->
            if (index == i) node.reduce(frame) else node
        }.toTypedArray()

        val new = SequenceNode(*newElements)


        return replace(new)
    }

    override fun reduce(frame: VirtualFrame): SequenceNode {
        val i = elements.indexOfFirst { it !is ValuesNode }
        val newElements = elements.mapIndexed { index, node ->
            if (index == i) node.reduce(frame) else node
        }.toTypedArray()

        val new = SequenceNode(*newElements)


        return replace(new)
    }

    val size: Int get() = elements.size

    fun isEmpty(): Boolean {
        return elements.isEmpty()
    }

    operator fun get(index: Int): TermNode {
        return elements[index]
    }

    fun sliceFrom(startIndex: Int, endIndexOffset: Int = 0): SequenceNode {
        val endIndex = size - endIndexOffset
        require(endIndexOffset >= 0 && endIndex <= size && startIndex <= endIndex) {
            "Invalid end index offset."
        }

        val sliced = elements.sliceArray(startIndex until endIndex)
        return SequenceNode(*sliced)
    }

    val head: TermNode get() = get(0)
    val tail: SequenceNode get() = sliceFrom(1)


    fun random(): TermNode {
        require(elements.isNotEmpty())
        return elements.random()
    }

    override val value: Any
        get() {
            return elements.joinToString { it.value.toString() }
        }

    override fun toString(): String {
        return elements.joinToString("") { it.value.toString() }
    }

    fun append(other: SequenceNode): SequenceNode {
        val newElements = mutableListOf<TermNode>()
        newElements.addAll(elements)
        newElements.addAll(other.elements)
        return SequenceNode(*newElements.toTypedArray())
    }
}