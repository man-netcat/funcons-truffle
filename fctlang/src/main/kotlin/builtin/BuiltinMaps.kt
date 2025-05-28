package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

private fun TermNode.extractKey(): TermNode {
    if (this !is ValueTupleNode) abort()
    return when (this.get(0).size) {
        1, 2 -> this.get(0).elements[0]
        else -> abort()
    }
}

private fun TermNode.extractValue(): TermNode {
    if (this !is ValueTupleNode) abort()
    return when (this.get(0).size) {
        1 -> SequenceNode()
        2 -> this.get(0).elements[1]
        else -> abort()
    }
}

data class ValueMapNode(@Child var vp0: SequenceNode = SequenceNode()) : MapsNode(GroundValuesNode(), ValuesNode()) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValueMapNode) return false

        val thisTuples = get(0).elements.toSet()
        val otherTuples = other.get(0).elements.toSet()
        return thisTuples == otherTuples
    }

    override fun hashCode(): Int {
        return get(0).elements
            .toSet()
            .hashCode()
    }

    override fun toString(): String {
        val elements = get(0).elements
        val str = elements.joinToString { tuple ->
            tuple as ValueTupleNode
            val tupleElements = tuple.get(0).elements
            when (tupleElements.size) {
                1 -> "${tupleElements[0]} |-> ()"
                2 -> "${tupleElements[0]} |-> ${tupleElements[1]}"
                else -> abort()
            }
        }
        return if (elements.isNotEmpty()) "{${str}}" else "map()"
    }
}

open class MapsNode(@Eager @Child var mapsTp0: TermNode, @Eager @Child var mapsTp1: TermNode) : GroundValuesNode(),
    MapsInterface

fun TermNode.isInMaps(): Boolean = this is ValueMapNode

class MapNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), MapInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return ValueMapNode(p0)
    }
}

class MapOverrideNode(@Eager @Child override var p0: SequenceNode) : TermNode(), MapOverrideInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val maps = get(0)

        val resultMap = linkedMapOf<TermNode, TermNode>()

        maps.elements.forEach { mapNode ->
            if (mapNode !is ValueMapNode) abort()
            mapNode.get(0).elements.forEach { tuple ->
                val key = tuple.extractKey()
                val value = tuple.extractValue()

                if (!resultMap.containsKey(key)) {
                    resultMap[key] = value
                }
            }
        }

        val tuples = resultMap.entries.map { (k, v) ->
            ValueTupleNode(SequenceNode(k, v))
        }.toTypedArray()

        return ValueMapNode(SequenceNode(*tuples))
    }
}

class MapDomainNode(@Eager @Child override var p0: TermNode) : TermNode(), MapDomainInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val mapNode = get(0)
        if (mapNode !is ValueMapNode) abort()

        val domainElements = mapNode.get(0).elements.map { tuple ->
            tuple.extractKey()
        }.toTypedArray()

        return ValueSetNode(SequenceNode(*domainElements))
    }
}

class MapUniteNode(@Eager @Child override var p0: SequenceNode) : TermNode(), MapUniteInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val maps = get(0)

        val resultMap = linkedMapOf<TermNode, TermNode>()

        maps.elements.forEach { mapNode ->
            if (mapNode !is ValueMapNode) abort()
            mapNode.get(0).elements.forEach { tuple ->
                val key = tuple.extractKey()
                val value = tuple.extractValue()

                if (resultMap.containsKey(key)) return SequenceNode()

                resultMap[key] = value
            }
        }

        val tuples = resultMap.entries.map { (k, v) ->
            ValueTupleNode(SequenceNode(k, v))
        }.toTypedArray()

        return ValueMapNode(SequenceNode(*tuples))
    }
}

class MapLookupNode(
    @Eager @Child override var p0: TermNode,
    @Eager @Child override var p1: TermNode,
) : TermNode(), MapLookupInterface {

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val mapNode = get(0)
        val keyNode = get(1)

        if (mapNode !is ValueMapNode) abort()

        mapNode.get(0).elements.forEach { tuple ->
            val key = tuple.extractKey()

            if (key == keyNode) return tuple.extractValue()
        }

        return SequenceNode()
    }
}

class MapElementsNode(
    @Eager @Child override var p0: TermNode,
) : TermNode(), MapElementsInterface {

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val mapNode = get(0)
        if (mapNode !is ValueMapNode) abort()

        val tuples = mapNode.get(0).elements.map { tuple ->
            if (tuple !is ValueTupleNode) abort()
            val tupleSeq = tuple.get(0)

            when (tupleSeq.size) {
                1 -> {
                    val key = tupleSeq.elements[0]
                    ValueTupleNode(SequenceNode(key))
                }

                2 -> {
                    val key = tupleSeq.elements[0]
                    val value = tupleSeq.elements[1]
                    ValueTupleNode(SequenceNode(key, value))
                }

                else -> abort()
            }
        }.toTypedArray()

        return SequenceNode(*tuples)
    }
}

class MapDeleteNode(
    @Eager @Child override var p0: TermNode,
    @Eager @Child override var p1: TermNode,
) : TermNode(), MapDeleteInterface {

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val mapNode = get(0)
        val setNode = get(1)

        if (mapNode !is ValueMapNode || setNode !is ValueSetNode) abort()

        val keysToDelete = setNode.get(0).elements

        if (keysToDelete.isEmpty()) return mapNode

        val keptTuples = mapNode.get(0).elements.filter { tuple ->
            val key = tuple.extractKey()
            key !in keysToDelete
        }.toTypedArray()

        return ValueMapNode(SequenceNode(*keptTuples))
    }
}

class MapEmptyNode : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode = ValueMapNode()
}
