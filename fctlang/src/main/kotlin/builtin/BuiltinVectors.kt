package builtin

class ValueVectorNode(@Child var p0: SequenceNode = SequenceNode()) : SetsNode(ValuesNode()) {
    override val value get() = "vector(${p0.value})"
}