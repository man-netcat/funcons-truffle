package main.debug

import cbs.CBSBaseVisitor
import org.antlr.v4.runtime.tree.RuleNode

class DebugExprVisitor : CBSBaseVisitor<Unit>() {
    var indent = 0

    override fun visitChildren(node: RuleNode) {
        println("  ".repeat(indent) + "${node::class.simpleName}")
        indent++
        super.visitChildren(node)
        indent--
    }
}