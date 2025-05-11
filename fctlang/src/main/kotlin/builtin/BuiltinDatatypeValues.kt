package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.DatatypeValuesInterface
import generated.IdentifierTaggedNode
import generated.StringsNode
import generated.isInIdentifiers
import language.NodeFactory.createNode

object ValueNodeFactory {
    private val strCache = mutableMapOf<String, ValueListNode>()
    private val charCache = mutableMapOf<Char, CharacterNode>()
    private val intCache = mutableMapOf<Int, TermNode>()
    private val valueCache = mutableMapOf<Pair<String, Int>, AbstractDatatypeValueNode>()

    fun datatypeValueNode(
        name: String,
        args: SequenceNode,
        constructor: () -> AbstractDatatypeValueNode
    ): AbstractDatatypeValueNode {
        return valueCache.getOrPut(name to args.hashCode(), constructor)
    }

    fun intNode(int: Int): TermNode = intCache.getOrPut(int) { IntegerNode(int) }

    fun strNode(str: String): ValueListNode = strCache.getOrPut(str) {
        val sequence = SequenceNode(*str.map { char -> charNode(char) }.toTypedArray())
        ValueListNode(sequence)
    }

    fun charNode(char: Char): CharacterNode = charCache.getOrPut(char) { CharacterNode(char) }
}

data class StringLiteralNode(override val value: String) : StringsNode() {
    override fun toString(): String = value
}

open class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

class DatatypeValueNode(@Eager @Child var p0: TermNode, @Eager @Child var p1: SequenceNode) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIdentifiers()) abort("datatype-value")

        val identifier = when (p0) {
            is IdentifierTaggedNode -> "TODO"
            is ValueListNode -> p0.elements.joinToString {
                it as CharacterNode
                it.value.toString()
            }

            else -> abort("datatype-value")
        }

        return createNode(identifier, listOf(*p1.elements))
    }
}

fun TermNode.isInDatatypeValues(): Boolean = this is AbstractDatatypeValueNode

abstract class AbstractDatatypeValueNode(var id: String, @Child var args: SequenceNode) :
    DatatypeValuesNode() {
    override fun toString() = if (args.isNotEmpty()) "$id(${args})" else "$id()"
}

data class ValueListNode(@Child var vp0: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode("list", SequenceNode(vp0)) {
    override fun toString(): String {
        return if (vp0.isNotEmpty() && vp0.elements.all { it is CharacterNode }) {
            vp0.elements.joinToString("") { it.toString() }
        } else if (vp0.isNotEmpty()) "[${vp0}]" else "[]"
    }
}

data class ValueTupleNode(@Child var vp0: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode("tuple", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueReturnedNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("returned", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueThrownNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("thrown", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueVariableNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    AbstractDatatypeValueNode("variable", SequenceNode(vp0, vp1)) {
    override fun toString() = super.toString()
}

data class ValueLinkNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("link", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueThunkNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("thunk", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueClassNode(@Child var vp0: TermNode, @Child var vp1: TermNode, @Child var vp2: TermNode) :
    AbstractDatatypeValueNode("class", SequenceNode(vp0, vp1, vp2)) {
    override fun toString() = super.toString()
}

data class ValueObjectNode(
    @Child var vp0: TermNode,
    @Child var vp1: TermNode,
    @Child var vp2: TermNode,
    @Child var vp3: TermNode,
) : AbstractDatatypeValueNode("object", SequenceNode(vp0, vp1, vp2, vp3)) {
    override fun toString() = super.toString()
}

data class ValueReferenceNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("reference", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValuePatternNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("pattern", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueFunctionNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("function", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueBitVectorNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("bit-vector", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueIdentifierTaggedNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    AbstractDatatypeValueNode("identifier-tagged", SequenceNode(vp0, vp1)) {
    override fun toString() = super.toString()
}

data class ValueContinuationNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("continuation", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueVariantNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    AbstractDatatypeValueNode("variant", SequenceNode(vp0, vp1)) {
    override fun toString() = super.toString()
}

data class ValueRecordNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode("record", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueVectorNode(@Child var vp0: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode("vector", SequenceNode(vp0)) {
    override fun toString() = super.toString()
}

data class ValueTreeNode(@Child var vp0: TermNode, @Child var vp1: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode("tree", SequenceNode(vp0, vp1)) {
    override fun toString() = super.toString()
}

class ValueFalseNode() : AbstractDatatypeValueNode("false", SequenceNode())
class ValueTrueNode() : AbstractDatatypeValueNode("true", SequenceNode())
class ValueBrokenNode() : AbstractDatatypeValueNode("broken", SequenceNode())
class ValueContinuedNode() : AbstractDatatypeValueNode("continued", SequenceNode())
class ValueFailedNode() : AbstractDatatypeValueNode("failed", SequenceNode())
class ValueDecimal128Node() : AbstractDatatypeValueNode("decimal128", SequenceNode())
class ValueDecimal64Node() : AbstractDatatypeValueNode("decimal64", SequenceNode())
class ValueBinary128Node() : AbstractDatatypeValueNode("binary128", SequenceNode())
class ValueBinary64Node() : AbstractDatatypeValueNode("binary64", SequenceNode())
class ValueBinary32Node() : AbstractDatatypeValueNode("binary32", SequenceNode())
class ValueSignalNode() : AbstractDatatypeValueNode("signal", SequenceNode())
class ValueNullValueNode() : AbstractDatatypeValueNode("null-value", SequenceNode())
class ValuePointerNullNode() : AbstractDatatypeValueNode("pointer-null", SequenceNode())

// TODO: At this stage, this could probably be automated actually, but I can't be bothered to rewrite this