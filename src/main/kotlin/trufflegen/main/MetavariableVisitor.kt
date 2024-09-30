package trufflegen.main

import org.antlr.v4.runtime.tree.RuleNode
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser

// Class to extract the variable string from metavariables
class MetavariableVisitor : CBSBaseVisitor<String>() {
    override fun visitVariable(ctx: CBSParser.VariableContext): String {
        return ctx.text
    }

    override fun visitVariableStep(ctx: CBSParser.VariableStepContext): String {
        return ctx.varname.text + "p".repeat(ctx.squote().size)
    }

    override fun visitSuffixExpression(ctx: CBSParser.SuffixExpressionContext): String {
        return visit(ctx.expr())
    }

    override fun visitChildren(node: RuleNode): String {
        "Unexpected child: ${node::class.simpleName}: ${node.text}"
        return super.visitChildren(node)
    }
}