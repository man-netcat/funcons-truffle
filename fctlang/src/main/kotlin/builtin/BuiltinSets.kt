package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

data class ValueSetNode(@Child var vp0: SequenceNode = SequenceNode()) : SetsNode(ValuesNode()) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValueSetNode) return false

        return this.vp0.elements.toSet() == other.vp0.elements.toSet()
    }

    override fun hashCode(): Int = vp0.elements.toSet().hashCode()
    override fun toString(): String = if (vp0.isNotEmpty()) "{${vp0}}" else "set()"
}

open class SetsNode(@Child var setsTp0: TermNode) : DatatypeValuesNode(), SetsInterface

fun TermNode.isInSets(): Boolean {
    return this::class in setOf(ValueSetNode::class)
}

class SetNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), SetInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val valueSet = p0.elements.toSet()
        return ValueSetNode(SequenceNode(*valueSet.toTypedArray()))
    }
}

class SetDifferenceNode(
    @Eager @Child override var p0: TermNode,
    @Eager @Child override var p1: TermNode,
) : TermNode(), SetDifferenceInterface {

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val set1 = get(0)
        val set2 = get(1)

        if (!set1.isInSets() || !set2.isInSets()) return FailNode()

        val elements1 = set1.get(0).elements
        val elements2 = set2.get(0).elements.toSet()

        val difference = elements1.filterNot { it in elements2 }.toSet()

        return ValueSetNode(SequenceNode(*difference.toTypedArray()))
    }
}

class SetElementsNode(@Eager @Child override var p0: TermNode) : TermNode(), SetElementsInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val set = get(0)

        if (!set.isInSets()) return FailNode()

        return set.get(0)
    }
}

class SetUniteNode(@Eager @Child override var p0: SequenceNode) : TermNode(), SetUniteInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val resultSet = LinkedHashSet<TermNode>()

        for (set in get(0).elements) {
            if (set !is ValueSetNode) return FailNode()
            resultSet.addAll(set.get(0).elements)
        }

        return ValueSetNode(SequenceNode(*resultSet.toTypedArray()))
    }
}

class SetSizeNode(
    @Eager @Child override var p0: TermNode
) : TermNode(), SetSizeInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return LengthNode(SequenceNode(SetElementsNode(p0)))
    }
}

class SomeElementNode(
    @Eager @Child override var p0: TermNode
) : TermNode(), SomeElementInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return IndexNode(IntegerNode(1), SequenceNode(SetElementsNode(get(0))))
    }
}

class ElementNotInNode(
    @Eager @Child override var p0: TermNode,
    @Eager @Child override var p1: TermNode,
) : TermNode(), ElementNotInInterface {

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val type = get(0)
        val set = get(1)

        if (!type.isInValueTypes()) return FailNode()
        if (!set.isInSets()) return FailNode()

        val elementsInSet = set.get(0).elements.map { it.value }.toSet()

        return when (type) {
            is BooleansNode -> {
                when {
                    "true" !in elementsInSet -> TrueNode()
                    "false" !in elementsInSet -> FalseNode()
                    else -> SequenceNode()
                }
            }

            is IntegersNode -> {
                var i = 0
                while (true) {
                    if (i !in elementsInSet) return NaturalNumberNode(i)
                    i++
                    if (i < 0) break
                }
                SequenceNode()
            }

            is CharactersNode -> {
                for (c in 'a'..'z') {
                    if (c !in elementsInSet) return CharacterNode(c)
                }
                SequenceNode()
            }

            is AtomsNode -> {
                var i = 1
                while (true) {
                    val value = "@$i"
                    if (value !in elementsInSet) return AtomNode(value)
                    i++
                    if (i < 0) break
                }
                SequenceNode()
            }

            else -> abort("element-not-in")
        }
    }
}

class SetInsertNode(
    override val p0: TermNode,
    override val p1: TermNode,
) : TermNode(), SetInsertInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val elements = get(1).get(0).elements.toMutableSet()

        elements.add(p0)

        return ValueSetNode(SequenceNode(*elements.toTypedArray()))
    }
}

class IsInSetNode(
    @Eager @Child override var p0: TermNode,
    @Eager @Child override var p1: TermNode,
) : TermNode(),
    IsInSetInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(1).get(0).elements.any { element -> element == p0 } -> TrueNode()
            else -> FalseNode()
        }
    }
}
