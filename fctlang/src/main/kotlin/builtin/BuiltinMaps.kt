package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

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
        return get(0).elements.joinToString(",") { tuple ->
            tuple as ValueTupleNode
            when (tuple.get(0).size) {
                1 -> "${tuple.get(0).elements[0]}|->()"
                2 -> "${tuple.get(0).elements[0]}|->${tuple.get(0).elements[1]}"
                else -> abort("value-map")
            }
        }
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
            if (mapNode !is ValueMapNode) abort("map-override")
            mapNode.get(0).elements.forEach { tuple ->
                if (tuple !is ValueTupleNode) abort("map-override")
                val key = tuple.get(0).elements[0]
                val value = when (tuple.get(0).size) {
                    1 -> SequenceNode()
                    2 -> tuple.get(0).elements[1]
                    else -> abort("map-override")
                }

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
        if (mapNode !is ValueMapNode) abort("map-domain")

        val domainElements = mapNode.get(0).elements.map { tuple ->
            if (tuple !is ValueTupleNode) abort("map-domain")
            when (tuple.get(0).size) {
                1, 2 -> tuple.get(0).elements[0]
                else -> abort("map-domain")
            }
        }.toTypedArray()

        return ValueSetNode(SequenceNode(*domainElements))
    }
}

class MapUniteNode(@Eager @Child override var p0: SequenceNode) : TermNode(), MapUniteInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val maps = get(0)

        val resultMap = linkedMapOf<TermNode, TermNode>()

        maps.elements.forEach { mapNode ->
            if (mapNode !is ValueMapNode) abort("map-override")
            mapNode.get(0).elements.forEach { tuple ->
                if (tuple !is ValueTupleNode) abort("map-override")
                val key = tuple.get(0).elements[0]
                val value = when (tuple.get(0).size) {
                    1 -> SequenceNode()
                    2 -> tuple.get(0).elements[1]
                    else -> abort("map-override")
                }

                if (resultMap.containsKey(key)) {
                    return SequenceNode()
                }

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

        if (mapNode !is ValueMapNode) abort("map-lookup")

        mapNode.get(0).elements.forEach { tuple ->
            if (tuple !is ValueTupleNode) abort("map-lookup")
            val tupleSeq = tuple.get(0)

            val key = when (tupleSeq.size) {
                1, 2 -> tupleSeq.elements[0]
                else -> abort("map-lookup")
            }

            if (key == keyNode) {
                val value = when (tupleSeq.size) {
                    1 -> SequenceNode()
                    2 -> tupleSeq.elements[1]
                    else -> abort("map-lookup")
                }
                return value
            }
        }

        return SequenceNode()
    }
}

class MapElementsNode(
    @Eager @Child override var p0: TermNode,
) : TermNode(), MapElementsInterface {

    override fun reduceRules(frame: VirtualFrame): TermNode {
        val mapNode = get(0)
        if (mapNode !is ValueMapNode) abort("map-elements")

        val tuples = mapNode.get(0).elements.map { tuple ->
            if (tuple !is ValueTupleNode) abort("map-elements")
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

                else -> abort("map-elements")
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

        if (mapNode !is ValueMapNode || setNode !is ValueSetNode) abort("map-delete")

        val keysToDelete = setNode.get(0).elements

        if (keysToDelete.isEmpty()) return mapNode

        val keptTuples = mapNode.get(0).elements.filter { tuple ->
            if (tuple !is ValueTupleNode) abort("map-delete")
            val key = when (tuple.get(0).size) {
                1, 2 -> tuple.get(0).elements[0]
                else -> abort("map-delete")
            }
            key !in keysToDelete
        }.toTypedArray()

        return ValueMapNode(SequenceNode(*keptTuples))
    }
}

class MapEmptyNode : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return ValueMapNode()
    }
}


