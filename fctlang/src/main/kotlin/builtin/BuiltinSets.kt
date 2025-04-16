package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class SetsNode(@Child var tp0: TermNode) : DatatypeValuesNode(), SetsInterface

fun TermNode.isInSets(): Boolean {
    return this::class in setOf(ValueSetNode::class)
}

class ValueSetNode(@Child var p0: SequenceNode = SequenceNode()) : SetsNode(ValuesNode()) {
    override val value get() = "{${p0.value}}"
}

class SetNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), SetInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return ValueSetNode(p0)
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
        val elements2 = set2.get(0).elements

        val difference = elements1.filterNot { it in elements2 }.toTypedArray()

        return ValueSetNode(SequenceNode(*difference))
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
        val resultSet = mutableSetOf<TermNode>()

        for (set in get(0).elements) {
            if (set !is ValueSetNode) return FailNode()
            resultSet.addAll(set.get(0).elements)
        }

        return ValueSetNode(SequenceNode(*resultSet.toTypedArray()))
    }
}

class SomeElementNode(@Eager @Child override var p0: TermNode) : TermNode(), SomeElementInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (get(0).get(0).elements.isEmpty()) return SequenceNode()

        return get(0).get(0).elements.random()
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
                var i = 0
                while (true) {
                    val atomStr = "@$i"
                    if (atomStr !in elementsInSet) return AtomNode(atomStr)
                    i++
                    if (i < 0) break
                }
                SequenceNode()
            }

            else -> FailNode()
        }
    }
}

class SetInsertNode(
    override val p0: TermNode,
    override val p1: TermNode,
) : TermNode(), SetInsertInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val elements = get(1).get(0).elements.toMutableList()

        if (elements.none { element -> element == p0 }) elements.add(p0)

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
