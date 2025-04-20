package builtin

import generated.*

data class ValueTupleNode(@Child var p0: SequenceNode = SequenceNode()) : TuplesNode() {
    override val value get() = "tuple(${p0.value})"
}

data class ValueListNode(@Child var p0: SequenceNode = SequenceNode()) : ListsNode(ValuesNode()) {
    override val value get() = "[${p0.value}]"
}

data class ValueReturnedNode(@Child var p0: TermNode) : ReturningNode() {
    override val value get() = "returned(${p0.value})"
}

data class ValueThrownNode(@Child var p0: TermNode) : ThrowingNode() {
    override val value get() = "thrown(${p0.value})"
}

data class ValueVariableNode(@Child var p0: TermNode, @Child var p1: TermNode) : VariablesNode() {
    override val value get() = "var(${p0.value}: ${p1.value})"
}

data class ValueLinkNode(@Child var p0: TermNode) : LinksNode() {
    override val value get() = "link(${p0.value})"
}

data class ValueThunkNode(@Child var p0: TermNode) : ThunksNode(AbstractionsNode(ValuesNode())) {
    override val value get() = "thunk(${p0.value})"
}

data class ValueClassNode(@Child var p0: TermNode, @Child var p1: TermNode, @Child var p2: TermNode) : ClassesNode() {
    override val value get() = "class(${p0.value},${p1.value},${p2.value})"
}

data class ValueObjectNode(
    @Child var p0: TermNode,
    @Child var p1: TermNode,
    @Child var p2: TermNode,
    @Child var p3: TermNode,
) : ObjectsNode() {
    override val value get() = "object(${p0.value},${p1.value},${p2.value},${p3.value})"
}

data class ValueReferenceNode(@Child var p0: TermNode) : ReferencesNode(ValuesNode()) {
    override val value get() = "reference(${p0.value})"
}

data class ValuePatternNode(@Child var p0: TermNode) : PatternsNode() {
    override val value get() = "pattern(${p0.value})"
}

data class ValueFunctionNode(@Child var p0: TermNode) : FunctionsNode(ValuesNode(), ValuesNode()) {
    override val value get() = "function(${p0.value})"
}

data class ValueBitVectorNode(@Child var p0: TermNode) : BitVectorsNode(NaturalNumbersNode()) {
    override val value get() = "bit-vector(${p0.value})"
}

data class ValueIdentifierTaggedNode(@Child var p0: TermNode, @Child var p1: TermNode) : Identifiers() {
    override val value get() = "identifier-tagged(${p1.value},${p0.value})"
}

data class ValueContinuationNode(@Child var p0: TermNode) : ContinuationsNode(ValuesNode(), ValuesNode()) {
    override val value get() = "continuations(${p0.value})"
}

data class ValueVariantNode(@Child var p0: TermNode, @Child var p1: TermNode) : VariantsNode(ValuesNode()) {
    override val value get() = "variants(${p0.value},${p1.value})"
}

data class ValueRecordNode(@Child var p0: TermNode) : RecordsNode(ValuesNode()) {
    override val value get() = "record(${p0.value})"
}
