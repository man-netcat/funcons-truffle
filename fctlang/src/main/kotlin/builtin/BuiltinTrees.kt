package builtin

import generated.TreesNode

class ValueTreeNode(@Child var p0: TermNode, @Child var p1: SequenceNode = SequenceNode()) : TreesNode(ValuesNode()) {
    override val value: String get() = "tree(${p0}" + if (p1.isNotEmpty()) ",${p1})" else ")"
}