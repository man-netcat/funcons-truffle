package builtin

import builtin.ValueNodeFactory.strLiteralNode
import com.oracle.truffle.api.frame.VirtualFrame
import generated.*
import language.NodeFactory.createNode

object ValueNodeFactory {
    private val strCache = mutableMapOf<String, ValueListNode>()
    private val charCache = mutableMapOf<Char, CharacterNode>()
    private val intCache = mutableMapOf<Int, TermNode>()
    private val valueCache = mutableMapOf<Pair<String, SequenceNode>, AbstractDatatypeValueNode>()
    private val atomCache = mutableMapOf<String, AtomNode>()
    private val strLiteralCache = mutableMapOf<String, StringLiteralNode>()

    fun datatypeValueNode(
        name: String,
        args: SequenceNode,
        constructor: () -> AbstractDatatypeValueNode
    ): AbstractDatatypeValueNode {
        return valueCache.getOrPut(name to args, constructor)
    }

    fun intNode(int: Int): TermNode = intCache.getOrPut(int) { IntegerNode(int) }
    fun charNode(char: Char): CharacterNode = charCache.getOrPut(char) { CharacterNode(char) }
    fun strNode(str: String): ValueListNode = strCache.getOrPut(str) {
        val sequence = SequenceNode(*str.map { char -> charNode(char) }.toTypedArray())
        ValueListNode(sequence)
    }

    fun atomNode(id: String): AtomNode = atomCache.getOrPut(id) { AtomNode(id) }

    fun strLiteralNode(str: String): StringLiteralNode = strLiteralCache.getOrPut(str) { StringLiteralNode(str) }
}

class StringLiteralNode(override val value: String) : StringsNode() {
    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = when (other) {
        is ValueListNode -> value == other.vp0.toStringLiteral()
        is StringLiteralNode -> value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
}

open class StringsNode : ListsNode(CharactersNode())

fun TermNode.isInStrings(): Boolean {
    return (this.isInLists() && this.get(0).elements.all { it.isInCharacters() }) || this is StringLiteralNode
}

open class AtomsNode : ValueTypesNode(), AtomsInterface

fun TermNode.isInAtoms(): Boolean = this is AtomsNode

class AtomNode(override val value: String) : AtomsNode() {
    override fun equals(other: Any?): Boolean = when (other) {
        is AtomNode -> this.value == other.value
        else -> false
    }

    override fun toString(): String = "atom($value)"
    override fun hashCode(): Int = value.hashCode()
}

open class DatatypeValuesNode : GroundValuesNode(), DatatypeValuesInterface

class DatatypeValueNode(@Eager @Child var p0: TermNode, @Eager @Child var p1: SequenceNode) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIdentifiers()) abort("datatype-value")
        if (p1.elements.any { it !is ValuesNode }) abort("datatype-value")
        val identifier = p0.toString()
        return createNode(identifier, listOf(*p1.elements))
    }
}

fun TermNode.isInDatatypeValues(): Boolean = this is AbstractDatatypeValueNode

abstract class AbstractDatatypeValueNode(
    @Child override var id: StringLiteralNode,
    @Child override var args: SequenceNode
) : DatatypeValuesNode() {
    override fun toString() = if (primaryCtor.parameters.isEmpty()) {
        "$id"
    } else if (args.isNotEmpty()) {
        "${id}(${args})"
    } else "${id}()"
}

class ValueListNode(@Child var vp0: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode(strLiteralNode("list"), SequenceNode(vp0)) {

    override fun toString(): String {
        return if (vp0.isNotEmpty()) vp0.toStringLiteral() ?: "[${vp0}]"
        else "[]"
    }


    override fun equals(other: Any?): Boolean = when (other) {
        is StringLiteralNode -> vp0.toStringLiteral() == other.value
        else -> super.equals(other)
    }

    override fun hashCode(): Int = vp0.toStringLiteral()?.hashCode() ?: super.hashCode()
}

class ValueTupleNode(@Child var vp0: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode(strLiteralNode("tuple"), SequenceNode(vp0))

class ValueReturnedNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("returned"), SequenceNode(vp0))

class ValueThrownNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("thrown"), SequenceNode(vp0))

class ValueVariableNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("variable"), SequenceNode(vp0, vp1))

class ValueLinkNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("link"), SequenceNode(vp0))

class ValueThunkNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("thunk"), SequenceNode(vp0))

class ValueClassNode(@Child var vp0: TermNode, @Child var vp1: TermNode, @Child var vp2: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("class"), SequenceNode(vp0, vp1, vp2))

class ValueObjectNode(
    @Child var vp0: TermNode,
    @Child var vp1: TermNode,
    @Child var vp2: TermNode,
    @Child var vp3: TermNode,
) : AbstractDatatypeValueNode(strLiteralNode("object"), SequenceNode(vp0, vp1, vp2, vp3))

class ValueReferenceNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("reference"), SequenceNode(vp0))

class ValuePatternNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("pattern"), SequenceNode(vp0))

class ValueFunctionNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("function"), SequenceNode(vp0))

class ValueBitVectorNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("bit-vector"), SequenceNode(vp0))

class ValueIdentifierTaggedNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("identifier-tagged"), SequenceNode(vp0, vp1))

class ValueContinuationNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("continuation"), SequenceNode(vp0))

class ValueVariantNode(@Child var vp0: TermNode, @Child var vp1: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("variant"), SequenceNode(vp0, vp1))

class ValueRecordNode(@Child var vp0: TermNode) :
    AbstractDatatypeValueNode(strLiteralNode("record"), SequenceNode(vp0))

class ValueVectorNode(@Child var vp0: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode(strLiteralNode("vector"), SequenceNode(vp0))

class ValueTreeNode(@Child var vp0: TermNode, @Child var vp1: SequenceNode = SequenceNode()) :
    AbstractDatatypeValueNode(strLiteralNode("tree"), SequenceNode(vp0, vp1))

class ValueFalseNode :
    AbstractDatatypeValueNode(strLiteralNode("false"), SequenceNode())

class ValueTrueNode :
    AbstractDatatypeValueNode(strLiteralNode("true"), SequenceNode())

class ValueBrokenNode :
    AbstractDatatypeValueNode(strLiteralNode("broken"), SequenceNode())

class ValueContinuedNode :
    AbstractDatatypeValueNode(strLiteralNode("continued"), SequenceNode())

class ValueFailedNode :
    AbstractDatatypeValueNode(strLiteralNode("failed"), SequenceNode())

class ValueDecimal128Node :
    AbstractDatatypeValueNode(strLiteralNode("decimal128"), SequenceNode())

class ValueDecimal64Node :
    AbstractDatatypeValueNode(strLiteralNode("decimal64"), SequenceNode())

class ValueBinary128Node :
    AbstractDatatypeValueNode(strLiteralNode("binary128"), SequenceNode())

class ValueBinary64Node :
    AbstractDatatypeValueNode(strLiteralNode("binary64"), SequenceNode())

class ValueBinary32Node :
    AbstractDatatypeValueNode(strLiteralNode("binary32"), SequenceNode())

class ValueSignalNode :
    AbstractDatatypeValueNode(strLiteralNode("signal"), SequenceNode())

class ValueNullValueNode :
    AbstractDatatypeValueNode(strLiteralNode("null-value"), SequenceNode())

class ValuePointerNullNode :
    AbstractDatatypeValueNode(strLiteralNode("pointer-null"), SequenceNode())

// TODO: At this stage, this could probably be automated actually, but I can't be bothered to rewrite this