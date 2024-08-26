package trufflegen.main

import org.antlr.v4.runtime.tree.RuleNode
import org.icecream.IceCream.ic
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*



class RewriteVisitor(private val params: List<Param>, private val ruleArgs: List<ExprContext>) :
    CBSBaseVisitor<String>() {
    override fun visitFunconExpr(funcon: FunconExprContext): String {
        val name = funcon.name.text
        val argStr = when (val rewriteArgs = funcon.args()) {
            is MultipleArgsContext -> rewriteArgs.exprs().joinExprsToString { visit(it) }
            is SingleArgsContext -> visit(rewriteArgs.expr())
            is NoArgsContext -> ""
            else -> throw IllegalStateException()
        }
        val className = toClassName(name)
        return "$className($argStr)"
    }

    override fun visitTupleExpression(tuple: TupleExpressionContext): String {
        return "(${
            tuple.tupleExpr().exprs().joinExprsToString {
                visit(it)
            }
        })"
    }

    override fun visitListExpression(list: ListExpressionContext): String {
        return "[${list.listExpr().exprs().joinExprsToString { visit(it) }}]"
    }

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): String {
        val visited = visit(suffixExpr.expr())
        return visited
    }

    override fun visitIdentifier(id: IdentifierContext): String {
        return id.text
    }

    override fun visitNumber(num: NumberContext): String {
        return num.text
    }

    override fun visitString(string: StringContext): String {
        return string.text
    }

    override fun visitChildren(node: RuleNode): String {
        ic(node.javaClass.name)
        return super.visitChildren(node)
    }
}