package interpreter

import generated.*


open class FCTIntegerNode(val value: Int) : IntegersNode()

fun Int.toIntegersNode(): IntegersNode {
    return FCTIntegerNode(this)
}

open class FCTStringNode(val value: String) : StringsNode()

fun String.toStringsNode(): StringsNode {
    return FCTStringNode(this)
}

@Funcon
class EmptyListNode : ListsNode<ValuesNode>()

@Funcon
class EmptySequenceNode : ValuesNode()