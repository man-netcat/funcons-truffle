package builtin

import generated.VectorsNode

class ValueVectorNode(@Child var p0: SequenceNode = SequenceNode()) : VectorsNode(ValuesNode()) {
    override val value get() = "vector(${p0.value})"
}