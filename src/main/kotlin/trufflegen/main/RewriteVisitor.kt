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

    override fun visitTupleExpression(tuple: TupleExpressionContext): String =
        "tuple(${visit(tuple.tupleExpr().exprs())})"

    override fun visitListExpression(list: ListExpressionContext): String = "listOf(${visit(list.listExpr().exprs())})"

    override fun visitSetExpression(set: SetExpressionContext): String = "setOf(${visit(set.setExpr().exprs())})"

    override fun visitMapExpression(map: MapExpressionContext): String =
        "hashMapOf(${visitPairs(map.mapExpr().pairs())})"

    private fun <T : RuleNode> visitSequences(nodes: List<T>): String = nodes.joinToString(", ") { visit(it) }

    override fun visitExprs(exprs: ExprsContext): String = visitSequences(exprs.expr())

    override fun visitPairs(pairs: PairsContext): String = visitSequences(pairs.pair())

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): String = rewriteExpr(suffixExpr)

    override fun visitVariable(varExpr: VariableContext): String = rewriteExpr(varExpr)

    override fun visitNumber(num: NumberContext): String = num.text

    override fun visitString(string: StringContext): String = string.text

    private fun rewriteExpr(expr: ParseTree): String {
        val isVararg = when (expr) {
            is SuffixExpressionContext -> true
            is VariableContext -> false
            else -> error("Unexpected expression type: ${expr::class.simpleName}")
        }

        val argIndex = ruleArgs.map(ArgVisitor(expr.text)::visit).indexOfFirst { it == true }
        val stringArgs = ruleArgs.map { it.text }
        if (argIndex == -1) throw Exception("String ${expr.text} not found in $stringArgs")

        val varargIndex = params.indexOfFirst { it.type.isVararg }

        val afterVararg = params.size - (varargIndex + 1)

        val paramStr = argToParam(varargIndex, afterVararg, argIndex, isVararg)

        return paramStr
    }

    private fun argToParam(varargIndex: Int, afterVararg: Int, argIndex: Int, isVararg: Boolean): String {
//        println("varargIndex: $varargIndex, after: $afterVararg, argIndex: $argIndex, isVararg: $isVararg")

        val paramList = params
        val totalParams = paramList.size

        return when {
            // Argument is in the pre-vararg section
            argIndex < varargIndex -> "${if (isVararg) "*" else ""}p$argIndex"

            // Argument is in the vararg section
            argIndex in varargIndex until (varargIndex + (totalParams - varargIndex - afterVararg)) -> {
                val varargIndexIndex = argIndex - varargIndex
                "p$varargIndex[$varargIndexIndex]"
            }

            // Argument is in the post-vararg section
            argIndex >= (varargIndex + (totalParams - varargIndex - afterVararg)) -> {
                val afterVarargIndex = argIndex - (totalParams - afterVararg)
                "p${varargIndex + 1 + afterVarargIndex}"
            }

            else -> throw IndexOutOfBoundsException()
        }
    }
}
