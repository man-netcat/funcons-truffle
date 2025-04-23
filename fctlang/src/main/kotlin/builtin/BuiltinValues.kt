package builtin

import generated.*

data class ValueMapNode(@Child var p0: SequenceNode = SequenceNode()) : MapsNode(GroundValuesNode(), ValuesNode()) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValueMapNode) return false

        val thisTuples = get(0).elements.map { it as ValueTupleNode }.toSet()
        val otherTuples = other.get(0).elements.map { it as ValueTupleNode }.toSet()
        return thisTuples == otherTuples
    }

    override fun hashCode(): Int {
        return get(0).elements
            .map { it as ValueTupleNode }
            .toSet()
            .hashCode()
    }

    override fun toString(): String {
        val str = get(0).elements.joinToString(",") { tuple ->
            tuple as ValueTupleNode
            when (tuple.get(0).size) {
                1 -> "${tuple.get(0).elements[0]}|->()"
                2 -> "${tuple.get(0).elements[0]}|->${tuple.get(0).elements[1]}"
                else -> abort("value-map")
            }
        }
        return if (get(0).isNotEmpty()) "{${str}}" else "map()"
    }
}

data class ValueSetNode(@Child var p0: SequenceNode = SequenceNode()) : SetsNode(ValuesNode()) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValueSetNode) return false

        return this.p0.elements.toSet() == other.p0.elements.toSet()
    }

    override fun hashCode(): Int = p0.elements.toSet().hashCode()
    override fun toString(): String = if (p0.isNotEmpty()) "{${p0}}" else "set()"
}

data class ValueTupleNode(@Child var p0: SequenceNode = SequenceNode()) : TuplesNode() {
    override fun toString() = if (p0.isNotEmpty()) "tuple(${p0})" else "tuple()"
}

data class ValueListNode(@Child var p0: SequenceNode = SequenceNode()) : ListsNode(ValuesNode()) {
    override fun toString(): String {
        return if (p0.isNotEmpty() && p0.elements.all { it is CharacterNode }) {
            p0.elements.joinToString("") { it.toString() }
        } else if (p0.isNotEmpty()) "[${p0}]" else "[]"
    }
}

data class ValueReturnedNode(@Child var p0: TermNode) : ReturningNode() {
    override fun toString() = "returned(${p0})"
}

data class ValueThrownNode(@Child var p0: TermNode) : ThrowingNode() {
    override fun toString() = "thrown(${p0})"
}

data class ValueVariableNode(@Child var p0: TermNode, @Child var p1: TermNode) : VariablesNode() {
    override fun toString() = "var(${p0}: ${p1})"
}

data class ValueLinkNode(@Child var p0: TermNode) : LinksNode() {
    override fun toString() = "link(${p0})"
}

data class ValueThunkNode(@Child var p0: TermNode) : ThunksNode(AbstractionsNode(ValuesNode())) {
    override fun toString() = "thunk(${p0})"
}

data class ValueClassNode(@Child var p0: TermNode, @Child var p1: TermNode, @Child var p2: TermNode) : ClassesNode() {
    override fun toString() = "class(${p0},${p1},${p2})"
}

data class ValueObjectNode(
    @Child var p0: TermNode,
    @Child var p1: TermNode,
    @Child var p2: TermNode,
    @Child var p3: TermNode,
) : ObjectsNode() {
    override fun toString() = "object(${p0},${p1},${p2},${p3})"
}

data class ValueReferenceNode(@Child var p0: TermNode) : ReferencesNode(ValuesNode()) {
    override fun toString() = "reference(${p0})"
}

data class ValuePatternNode(@Child var p0: TermNode) : PatternsNode() {
    override fun toString() = "pattern(${p0})"
}

data class ValueFunctionNode(@Child var p0: TermNode) : FunctionsNode(ValuesNode(), ValuesNode()) {
    override fun toString() = "function(${p0})"
}

data class ValueBitVectorNode(@Child var p0: TermNode) : BitVectorsNode(NaturalNumbersNode()) {
    override fun toString() = "bit-vector(${p0})"
}

data class ValueIdentifierTaggedNode(@Child var p0: TermNode, @Child var p1: TermNode) : Identifiers() {
    override fun toString() = "identifier-tagged(${p1},${p0})"
}

data class ValueContinuationNode(@Child var p0: TermNode) : ContinuationsNode(ValuesNode(), ValuesNode()) {
    override fun toString() = "continuations(${p0})"
}

data class ValueVariantNode(@Child var p0: TermNode, @Child var p1: TermNode) : VariantsNode(ValuesNode()) {
    override fun toString() = "variants(${p0},${p1})"
}

data class ValueRecordNode(@Child var p0: TermNode) : RecordsNode(ValuesNode()) {
    override fun toString() = "record(${p0})"
}

data class ValueDatatypeValueNode(@Child var p0: TermNode, @Child var p1: SequenceNode) : DatatypeValuesNode() {
    override fun toString() = "datatype-value(${p0},${p0})"
}

class ValueVectorNode(@Child var p0: SequenceNode = SequenceNode()) : VectorsNode(ValuesNode()) {
    override fun toString() = if (p0.isNotEmpty()) "vector(${p0})" else "vector()"
}

class ValueTreeNode(@Child var p0: TermNode, @Child var p1: SequenceNode = SequenceNode()) : TreesNode(ValuesNode()) {
    override fun toString() = "tree(${p0}" + if (p1.isNotEmpty()) ",${p1})" else ")"
}