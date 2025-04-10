package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class MapsNode(var tp0: TermNode, var tp1: TermNode) : ValueTypesNode(), MapsInterface

fun TermNode.isInMaps(): Boolean = this is ValueMapNode

class MapNode(@Eager @Child override var p0: SequenceNode = SequenceNode()) : TermNode(), MapInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return ValueMapNode(p0)
    }
}

class ValueMapNode(@Child var p0: SequenceNode = SequenceNode()) : MapsNode(GroundValuesNode(), ValuesNode()) {
    override val value
        get() = "{${
            get(0).elements.joinToString { tuple ->
                tuple as ValueTupleNode
                when (tuple.get(0).size) {
                    0 -> "map( )"
                    1 -> "${tuple.get(0).elements[0].value} |-> ( )"
                    2 -> "${tuple.get(0).elements[0].value} |-> ${tuple.get(0).elements[1].value}"
                    else -> abort("value-map")
                }
            }
        }}"
}

class MapOverrideNode(@Eager @Child override var p0: SequenceNode) : TermNode(), MapOverrideInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val resultMap = linkedMapOf<TermNode, TermNode>()

        get(0).elements.forEach { mapNode ->
            mapNode as ValueMapNode
            mapNode.get(0).elements.forEach { tuple ->
                tuple as ValueTupleNode
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
        if (get(0) !is ValueMapNode) return FailNode()

        val domainElements = get(0).get(0).elements.map { tuple ->
            tuple as ValueTupleNode
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
        val mapNode = get(0)
        val resultMap = linkedMapOf<TermNode, TermNode>()

        mapNode.elements.forEach { mapNode ->
            mapNode as ValueMapNode
            mapNode.get(0).elements.forEach { tuple ->
                tuple as ValueTupleNode
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

        if (mapNode !is ValueMapNode) return FailNode()

        for (tuple in mapNode.get(0).elements) {
            tuple as ValueTupleNode
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
        if (mapNode !is ValueMapNode) return FailNode()

        val tuples = mapNode.get(0).elements.map { tuple ->
            tuple as ValueTupleNode
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

        if (mapNode !is ValueMapNode || setNode !is ValueSetNode) return FailNode()

        val keysToDelete = setNode.get(0).elements

        if (keysToDelete.isEmpty()) return mapNode

        val keptTuples = mapNode.get(0).elements.filter { tuple ->
            tuple as ValueTupleNode
            val key = tuple.get(0).elements[0]
            key !in keysToDelete
        }

        return ValueMapNode(SequenceNode(*keptTuples.toTypedArray()))
    }
}

