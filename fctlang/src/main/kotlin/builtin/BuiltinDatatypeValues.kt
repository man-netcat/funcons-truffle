package builtin

import builtin.ValueNodeFactory.strLiteralNode
import com.oracle.truffle.api.frame.VirtualFrame
import generated.DatatypeValuesInterface
import language.NodeFactory.createNode

object ValueNodeFactory {
    private val strCache = mutableMapOf<String, ValueListNode>()
    private val charCache = mutableMapOf<Char, CharacterNode>()
    private val intCache = mutableMapOf<Int, TermNode>()
    private val strLiteralCache = mutableMapOf<String, StringLiteralNode>()

    fun intNode(int: Int): TermNode = intCache.getOrPut(int) { IntegerNode(int) }

    fun strNode(str: String): ValueListNode = strCache.getOrPut(str) {
        val sequence = SequenceNode(*str.map { char -> charNode(char) }.toTypedArray())
        ValueListNode(sequence)
    }

    fun charNode(char: Char): CharacterNode = charCache.getOrPut(char) { CharacterNode(char) }

    fun strLiteralNode(str: String): StringLiteralNode = strLiteralCache.getOrPut(str) { StringLiteralNode(str) }
}

data class StringLiteralNode(override val value: String) : GroundValuesNode() {
    override fun toString(): String = value
}

open class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

class DatatypeValueNode(@Eager @Child var p0: TermNode, @Eager @Child var p1: SequenceNode) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIdentifiers()) abort("datatype-value")

        val identifier = when (p0) {
            is IdentifierTaggedNode -> "TODO"
            is StringLiteralNode -> p0.value.toString()
            else -> abort("datatype-value")
        }

        return createNode(identifier, listOf(*p1.elements))
    }
}

fun TermNode.isInDatatypeValues(): Boolean = this is ValueDatatypeValueNode

abstract class ValueDatatypeValueNode(@Child var p0: TermNode, @Child var p1: SequenceNode) :
    DatatypeValuesNode() {
    override fun toString() = if (p1.isNotEmpty()) "$p0(${p1})" else "$p0()"
}

data class ValueListNode(@Child var vp0: SequenceNode = SequenceNode()) :
    ValueDatatypeValueNode(strLiteralNode("list"), SequenceNode(vp0)) {
    override fun toString(): String {
        return if (vp0.isNotEmpty() && vp0.elements.all { it is CharacterNode }) {
            vp0.elements.joinToString("") { it.toString() }
        } else if (vp0.isNotEmpty()) "[${vp0}]" else "[]"
    }
}

data class ValueTupleNode(@Child var vp0: SequenceNode = SequenceNode()) :
    ValueDatatypeValueNode(strLiteralNode("tuple"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueReturnedNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("returned"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueThrownNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("thrown"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueVariableNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("variable"), SequenceNode(vp0, vp1)) {
    override fun toString() = super.toString()
}

data class ValueLinkNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("link"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueThunkNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("thunk"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueClassNode(@Child var vp0: TermNode, @Child var vp1: TermNode, @Child var vp2: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("class"), SequenceNode(vp0, vp1, vp2)) {
    override fun toString() = super.toString()
}

data class ValueObjectNode(
    @Child var vp0: TermNode,
    @Child var vp1: TermNode,
    @Child var vp2: TermNode,
    @Child var vp3: TermNode,
) : ValueDatatypeValueNode(strLiteralNode("object"), SequenceNode(vp0, vp1, vp2, vp3)) {
    override fun toString() = super.toString()
}

data class ValueReferenceNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("reference"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValuePatternNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("pattern"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueFunctionNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("function"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueBitVectorNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("bit-vector"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueIdentifierTaggedNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("identifier-tagged"), SequenceNode(vp0, vp1)) {
    override fun toString() = super.toString()
}

data class ValueContinuationNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("continuation"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueVariantNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("variant"), SequenceNode(vp0, vp1)) {
    override fun toString() = super.toString()
}

data class ValueRecordNode(@Child var vp0: TermNode) :
    ValueDatatypeValueNode(strLiteralNode("record"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueVectorNode(@Child var vp0: SequenceNode = SequenceNode()) :
    ValueDatatypeValueNode(strLiteralNode("vector"), SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueTreeNode(@Child var vp0: TermNode, @Child var vp1: SequenceNode = SequenceNode()) :
    ValueDatatypeValueNode(strLiteralNode("tree"), SequenceNode(vp0, vp1)) {
    override fun toString() = super.toString()
}
