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

        if (set1 !is ValueSetNode || set2 !is ValueSetNode) return FailNode()

        val elements1 = set1.get(0).elements
        val elements2 = set2.get(0).elements.toSet()

        val difference = elements1.filterNot { it in elements2 }.toTypedArray()

        return ValueSetNode(SequenceNode(*difference))
    }
}

class SetElementsNode(@Eager @Child override var p0: TermNode) : TermNode(), SetElementsInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val set = get(0)

        if (set !is ValueSetNode) return FailNode()

        return set.get(0)
    }
}

class SetUniteNode(@Eager @Child override var p0: SequenceNode) : TermNode(), SetUniteInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (get(0).elements.isEmpty()) {
            return ValueSetNode(SequenceNode())
        }

        val result = LinkedHashSet<TermNode>()

        get(0).elements.forEach { term ->
            if (term !is ValueSetNode) return FailNode()

            term.get(0).elements.forEach { element ->
                result.add(element)
            }
        }

        return ValueSetNode(SequenceNode(*result.toTypedArray()))
    }
}

class SomeElementNode(@Eager @Child override var p0: SequenceNode) : TermNode(), SomeElementInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (p0.elements.isEmpty()) return SequenceNode()

        return p0.random()
    }
}

//class ElementNotInNode(
//    @Eager @Child override var p0: TermNode,
//    @Eager @Child override var p1: TermNode,
//) : TermNode(), ElementNotInInterface {
//
//    override fun reduceRules(frame: VirtualFrame): TermNode {
//        val type = get(0)
//        val set = get(1)
//
//        if (set !is ValueSetNode) return FailNode()
//
//        val elementsInSet = set.elements.toSet()
//
//        return when (type) {
//            // Might need more types
//            is BooleansNode -> {
//                when {
//                    TrueNode() !in elementsInSet -> TrueNode()
//                    FalseNode() !in elementsInSet -> FalseNode()
//                    else -> SequenceNode()
//                }
//            }
//
//            is IntegersNode -> {
//                var i = 0
//                while (true) {
//                    val candidate = NaturalNumberNode(i)
//                    if (candidate !in elementsInSet) return candidate
//                    i++
//                    if (i < 0) break
//                }
//                SequenceNode()
//            }
//
//            is CharactersNode -> {
//                for (c in 'a'..'z') {
//                    val candidate = CharacterNode(c)
//                    if (candidate !in elementsInSet) return candidate
//                }
//                SequenceNode()
//            }
//
//            else -> FailNode()
//        }
//    }
//}
//
//class IsInSetNode(
//    @Eager @Child override var p0: TermNode,
//    @Eager @Child override var p1: TermNode,
//) : TermNode(),
//    IsInSetInterface {
//    override fun reduceRules(frame: VirtualFrame): TermNode {
//        return when {
//            p1.elements.any { element -> element == p0 } -> TrueNode()
//            else -> FalseNode()
//        }
//    }
//}
//
//class SetElementsNode(@Eager @Child override var p0: TermNode) : TermNode(), SetElementsInterface {
//    override fun reduceRules(frame: VirtualFrame): TermNode {
//        if (p0 !is ValueSetNode) return FailNode()
//
//        return SequenceNode(*p0.elements)
//    }
//}
//
