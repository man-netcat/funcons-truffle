package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.SetInterface
import generated.SetsInterface

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
