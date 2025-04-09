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
            p0.elements.joinToString { tuple ->
                tuple as ValueTupleNode
                require(tuple.p0.size == 2) { "Invalid map" }
                "${tuple.p0.elements[0].value} |-> ${tuple.p0.elements[1].value}"
            }
        }}"
}

class MapLookupNode(@Child override var p0: TermNode, @Child override var p1: TermNode) : TermNode(),
    MapLookupInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(0).isInValues() && get(1).isInGroundValues() -> {
                get(0).get(0).elements.firstOrNull { element -> element == get(1) } ?: SequenceNode()
            }

            else -> FailNode()
        }
    }
}

class MapOverrideNode(@Eager @Child override var p0: SequenceNode) : TermNode(), MapOverrideInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val resultMap = linkedMapOf<TermNode, TermNode>()

        p0.elements.forEach { mapNode ->
            mapNode as ValueMapNode
            mapNode.p0.elements.forEach { tuple ->
                tuple as ValueTupleNode
                require(tuple.p0.size == 2) { "Invalid map entry" }

                val key = tuple.p0.elements[0]
                val value = tuple.p0.elements[1]

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

class MapDomainNode(@Child override var p0: TermNode) : TermNode(), MapDomainInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        return when {
            get(0) is ValueMapNode && get(1) is GroundValuesNode -> {
                get(0).get(0).elements.firstOrNull { element -> element == get(1) } ?: SequenceNode()
            }

            else -> FailNode()
        }
    }
}

class MapUniteNode(@Eager @Child override var p0: SequenceNode) : TermNode(), MapUniteInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        val resultMap = linkedMapOf<TermNode, TermNode>()

        p0.elements.forEach { mapNode ->
            mapNode as ValueMapNode
            mapNode.p0.elements.forEach { tuple ->
                tuple as ValueTupleNode
                require(tuple.p0.size == 2) { "Invalid map entry" }

                val key = tuple.p0.elements[0]
                val value = tuple.p0.elements[1]

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
