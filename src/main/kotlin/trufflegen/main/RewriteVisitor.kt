package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class RewriteVisitor(private val params: List<Param>, private val ruleArgs: List<ExprContext>) :
    CBSBaseVisitor<String>() {

    override fun visitFunconExpr(funcon: FunconExprContext): String {
        val name = funcon.name.text
        val argStr = when (val rewriteArgs = funcon.args()) {
            is MultipleArgsContext -> visit(rewriteArgs.exprs())
            is SingleArgsContext -> visit(rewriteArgs.expr())
            is NoArgsContext -> ""
            else -> throw IllegalStateException()
        }
        val className = toClassName(name)
        return "$className($argStr)"
    }

    override fun visitTupleExpression(tuple: TupleExpressionContext): String {
        val exprs = tuple.tupleExpr().exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "null" else "tuple(${visit(tuple.tupleExpr().exprs())})"
    }

    override fun visitListExpression(list: ListExpressionContext): String {
        val exprs = list.listExpr().exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "emptyList()" else "listOf(${visit(list.listExpr().exprs())})"
    }

    override fun visitSetExpression(set: SetExpressionContext): String = "setOf(${visit(set.setExpr().exprs())})"

    override fun visitMapExpression(map: MapExpressionContext): String =
        "hashMapOf(${visitPairs(map.mapExpr().pairs())})"

    private fun <T : RuleNode> visitSequences(nodes: List<T>): String = nodes.joinToString(", ") { visit(it) }

    override fun visitExprs(exprs: ExprsContext): String = visitSequences(exprs.expr())

    override fun visitPairs(pairs: PairsContext): String = visitSequences(pairs.pair())

    override fun visitPair(pair: PairContext): String = "${pair.key.text} to ${pair.value.text}"

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): String = rewriteExpr(suffixExpr)

    override fun visitVariable(varExpr: VariableContext): String = rewriteExpr(varExpr)

    override fun visitNumber(num: NumberContext): String = num.text

    override fun visitString(string: StringContext): String = string.text

    private fun rewriteExpr(expr: ParseTree): String {
        val argIsVararg = when (expr) {
            is SuffixExpressionContext -> true
            is VariableContext -> false
            else -> error("Unexpected expression type: ${expr::class.simpleName}")
        }

        val argIndex = ruleArgs.map(ArgVisitor(expr.text)::visit).indexOfFirst { it == true }

        if (argIndex == -1) {
            val stringArgs = ruleArgs.map { it.text }
            throw Exception("String ${expr.text} not found in $stringArgs")
        }

        val paramVarargIndex = params.indexOfFirst { it.type.isVararg }

        val afterVararg = params.size - (paramVarargIndex + 1)

        val paramStr = argToParam(paramVarargIndex, afterVararg, argIndex, argIsVararg)

        return paramStr
    }

    private fun argToParam(paramVarargIndex: Int, paramsAfterVararg: Int, argIndex: Int, isVararg: Boolean): String {
//        println("varargIndex: $varargIndex, after: $afterVararg, argIndex: $argIndex, isVararg: $isVararg")

        return when {
            // Argument is in the pre-vararg section
            argIndex < paramVarargIndex -> "${if (isVararg) "*" else ""}p$argIndex"

            // Argument is in the vararg section
            argIndex in paramVarargIndex until (paramVarargIndex + (params.size - paramVarargIndex - paramsAfterVararg)) -> {
                val varargIndexIndex = argIndex - paramVarargIndex
                "p$paramVarargIndex[$varargIndexIndex]"
            }

            // Argument is in the post-vararg section
            argIndex >= (paramVarargIndex + (params.size - paramVarargIndex - paramsAfterVararg)) -> {
                val afterVarargIndex = argIndex - (params.size - paramsAfterVararg)
                "p${paramVarargIndex + 1 + afterVarargIndex}"
            }

            else -> throw IndexOutOfBoundsException()
        }
    }
}
