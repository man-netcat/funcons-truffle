package language

import com.oracle.truffle.api.frame.VirtualFrame

@Builtin
open class SequenceNode(@Children vararg var elements: TermNode) : TermNode() {
    override val nonLazy: List<Int>
        get() = elements.indices.toList()

    init {
        elements = elements.flatMap {
            if (it is SequenceNode) it.elements.toList() else listOf(it)
        }.toTypedArray()
    }

    override fun reduceComputations(frame: VirtualFrame): TermNode? {
        val newParams = elements.toMutableList()
        var attemptedReduction = false

        for (index in nonLazy) {
            if (newParams[index].isReducible()) {
                try {
                    attemptedReduction = true
                    newParams[index] = newParams[index].reduce(frame)
                    val new = SequenceNode(*newParams.toTypedArray())
                    return replace(new)
                } catch (e: Exception) {
                    println(e)
                }
            }
        }

        return if (!attemptedReduction) null
        else throw IllegalStateException("All reductions failed")
    }

    override fun isReducible(): Boolean {
        return elements.any { it.isReducible() }
    }

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val new = ValueSequenceNode(*elements)
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