package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class MapsNode(@Eager @Child var tp0: TermNode, @Eager @Child var tp1: TermNode) : GroundValuesNode(),
    MapsInterface

fun TermNode.isInMaps(): Boolean = this is ValueMapNode

class MapNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), MapInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return ValueMapNode(p0)
    }
}

class ValueMapNode(@Child var p0: SequenceNode = SequenceNode()) : MapsNode(GroundValuesNode(), ValuesNode()) {
    override val value: String
        get() {
            val str = get(0).elements.joinToString(",") { tuple ->
                tuple as ValueTupleNode
                when (tuple.get(0).size) {
                    1 -> "${tuple.get(0).elements[0].value}|->()"
                    2 -> "${tuple.get(0).elements[0].value}|->${tuple.get(0).elements[1].value}"
                    else -> abort("value-map")
                }
            }
            return if (get(0).isNotEmpty()) "{${str}}" else "map()"
        }
}

class MapOverrideNode(@Eager @Child override var p0: SequenceNode) : TermNode(), MapOverrideInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val maps = get(0)

        val resultMap = linkedMapOf<TermNode, TermNode>()

        maps.elements.forEach { mapNode ->
            if (mapNode !is ValueMapNode) return abort("map-override")
            mapNode.get(0).elements.forEach { tuple ->
                if (tuple !is ValueTupleNode) return abort("map-override")
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
        if (mapNode !is ValueMapNode) return abort("map-domain")

        val domainElements = mapNode.get(0).elements.map { tuple ->
            if (tuple !is ValueTupleNode) return abort("map-domain")
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
            if (mapNode !is ValueMapNode) return abort("map-override")
            mapNode.get(0).elements.forEach { tuple ->
                if (tuple !is ValueTupleNode) return abort("map-override")
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

        if (mapNode !is ValueMapNode) return abort("map-lookup")

        mapNode.get(0).elements.forEach { tuple ->
            if (tuple !is ValueTupleNode) return abort("map-lookup")
            val tupleSeq = tuple.get(0)

            val key = when (tupleSeq.size) {
                1, 2 -> tupleSeq.elements[0]
                else -> abort("map-lookup")
            }

            if (key.value == keyNode.value) {
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
        if (mapNode !is ValueMapNode) return abort("map-elements")

        val tuples = mapNode.get(0).elements.map { tuple ->
            if (tuple !is ValueTupleNode) return abort("map-elements")
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

        if (mapNode !is ValueMapNode || setNode !is ValueSetNode) return abort("map-delete")

        val keysToDelete = setNode.get(0).elements

        if (keysToDelete.isEmpty()) return mapNode

        val keptTuples = mapNode.get(0).elements.filter { tuple ->
            if (tuple !is ValueTupleNode) return abort("map-delete")
            val (key, _) = tuple.get(0).elements
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


