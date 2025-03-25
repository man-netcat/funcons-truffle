package language

import com.oracle.truffle.api.frame.VirtualFrame
import generated.NullValueNode
import language.Util.DEBUG

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

        for (index in nonLazy) {
            if (newParams[index].isReducible()) {
                if (DEBUG) println("attempting to reduce ${newParams[index]::class.simpleName} in ${this::class.simpleName}")
                try {
                    newParams[index] = newParams[index].reduce(frame)
                    val new = SequenceNode(*newParams.toTypedArray())
                    return replace(new)
                } catch (e: Exception) {
                    if (DEBUG) println("stuck in ${this::class.simpleName} with error $e")
                }
            }
        }

        return null
    }

    override fun reduceRules(frame: VirtualFrame): TermNode {
        abort("sequence")
    }

    val size: Int get() = elements.size

    fun isEmpty(): Boolean {
        return elements.isEmpty()
    }

    operator fun get(index: Int): TermNode {
        return try {
            elements[index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            NullValueNode()
        }
    }

    fun sliceFrom(startIndex: Int, endIndexOffset: Int = 0): SequenceNode {
        val endIndex = size - endIndexOffset
        require(endIndexOffset >= 0 && endIndex <= size && startIndex <= endIndex) {
            "Invalid end index offset."
        }

        val sliced = elements.sliceArray(startIndex until endIndex)
        return SequenceNode(*sliced)
    }

    val head: TermNode = get(0)
    val second: TermNode = get(1)
    val third: TermNode = get(2)
    val fourth: TermNode = get(3)
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