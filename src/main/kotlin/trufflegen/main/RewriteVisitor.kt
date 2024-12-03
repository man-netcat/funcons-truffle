package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class RewriteVisitor(val definition: ParseTree) : CBSBaseVisitor<String>() {
    val callStack: ArrayDeque<String> = ArrayDeque()

    override fun visitFunconExpression(funcon: FunconExpressionContext): String {
        val name = funcon.name.text
        val obj = globalObjects[name]!!

        callStack.addLast(name)
        val argList = makeArgList(funcon.args())
        val argStr = argList.joinToString { visit(it) }

        callStack.removeLast()
        val className = toClassName(name)
        return "$className($argStr)"
    }

    override fun visitTupleExpression(tuple: TupleExpressionContext): String {
        val exprs = tuple.exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "EmptySequenceNode()" else "ListNode(${visit(tuple.exprs())})"
    }

    override fun visitListExpression(list: ListExpressionContext): String {
        val exprs = list.exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "EmptyListNode()" else "ListNode(${visit(list.exprs())})"
    }

    override fun visitSetExpression(set: SetExpressionContext): String {
        val expr = set.exprs()?.expr()
        return if (expr == null) "EmptySetNode()" else "SetNode(${visit(set.exprs())})"
    }

    override fun visitMapExpression(map: MapExpressionContext): String = "MapsNode(${visitPairs(map.pairs())})"
    private fun visitSequences(nodes: List<ParseTree>, sep: String = ", "): String =
        nodes.joinToString(sep) { visit(it) }

    override fun visitPairs(pairs: PairsContext): String = visitSequences(pairs.pair())
    override fun visitPair(pair: PairContext): String = "${visit(pair.key)} to ${visit(pair.value)}"
    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): String {
        return exprToParamStr(definition, suffixExpr.text) + when (suffixExpr.op.text) {
            "?" -> "?"
            else -> ""
        }
    }

    override fun visitVariable(varExpr: VariableContext): String = exprToParamStr(definition, varExpr.varname.text)

    override fun visitNumber(num: NumberContext): String = "(${num.text}).toIntegersNode()"
    override fun visitString(string: StringContext): String = "(${string.text}).toStringsNode()"
    override fun visitTypeExpression(typeExpr: TypeExpressionContext): String = visit(typeExpr.value)

    override fun visitNestedExpression(ctx: NestedExpressionContext?): String = visit(ctx?.expr())
}
