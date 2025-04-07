package builtin
//
//import com.oracle.truffle.api.frame.VirtualFrame
//import generated.SetInterface
//import generated.SetsInterface
//
//
//open class SetsNode(@Child var tp0: TermNode) : DatatypeValuesNode(), SetsInterface
//
//fun TermNode.isInSets(): Boolean {
//    return this::class in setOf(ValueSetNode::class)
//}
//
//class ValueSetNode(@Child var p0: SequenceNode = SequenceNode()) : SetsNode(ValuesNode()) {
//    override val value get() = "{${p0.value}}"
//}
//
//class SetNode(@Child override var p0: SequenceNode = SequenceNode()) : TermNode(), SetInterface {
//    override val eager = listOf(0)
//    override fun reduceRules(frame: VirtualFrame): TermNode {
//        return ValueSetNode(p0)
//    }
//}