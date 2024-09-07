package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.icecream.IceCream.ic
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class RewriteVisitor(
    private val rootExpr: ParseTree, private val params: List<Param>, private val ruleArgs: List<ExprContext?>
) : CBSBaseVisitor<String>() {

    override fun visitFunconExpression(funcon: FunconExpressionContext): String {
        val name = funcon.name.text
        val argStr = when (val rewriteArgs = funcon.args()) {
            is MultipleArgsContext -> visit(rewriteArgs.exprs())
            is SingleArgsContext -> visit(rewriteArgs.expr())
            is NoArgsContext -> ""
            else -> throw DetailedException("Unexpected arg type: ${rewriteArgs::class.simpleName}")
        }
        val className = toClassName(name)
        return "$className($argStr)"
    }

    override fun visitTupleExpression(tuple: TupleExpressionContext): String {
        val exprs = tuple.exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "emptyList()" else "listOf(${visit(tuple.exprs())})"
    }

    override fun visitListExpression(list: ListExpressionContext): String {
        val exprs = list.exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "emptyList()" else "listOf(${visit(list.exprs())})"
    }

    override fun visitSetExpression(set: SetExpressionContext): String {
        val exprs = set.exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "emptySet()" else "setOf(${visit(set.exprs())})"
    }

    override fun visitMapExpression(map: MapExpressionContext): String = "hashMapOf(${visitPairs(map.pairs())})"

    private fun visitSequences(nodes: List<ParseTree>, sep: String = ", "): String =
        nodes.joinToString(sep) { visit(it) }

    override fun visitExprs(exprs: ExprsContext): String = visitSequences(exprs.expr())

    override fun visitPairs(pairs: PairsContext): String = visitSequences(pairs.pair())

    override fun visitPair(pair: PairContext): String = "${visit(pair.key)} to ${visit(pair.value)}"

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): String = rewriteExpr(suffixExpr)

    override fun visitVariable(varExpr: VariableContext): String = rewriteExpr(varExpr)

    override fun visitVariableStep(varStep: VariableStepContext): String = rewriteExpr(varStep)

    override fun visitNumber(num: NumberContext): String = num.text

    override fun visitString(string: StringContext): String = string.text

    private fun rewriteExpr(expr: ParseTree): String {
        val (text, paramIsArray) = when (expr) {
            is SuffixExpressionContext -> expr.text to true
            is VariableContext -> expr.varname.text to false
            is VariableStepContext -> expr.varname.text to false
            else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}")
        }

        val exprIsArg = when (rootExpr) {
            is FunconExpressionContext, is ListIndexExpressionContext, is ListExpressionContext -> true
            else -> false
        }

//        println("paramIsArray: $paramIsArray, exprIsArg: $exprIsArg")

        val argIndex = ruleArgs.indexOfFirst { ArgVisitor(text).visit(it) }
        if (argIndex == -1) {
            val stringArgs = ruleArgs.map { it?.text }
            throw DetailedException("String '$text' not found in $stringArgs")
        }

        val paramVarargIndex = params.indexOfFirst { it.type.isVararg }

        val afterVararg = params.size - (paramVarargIndex + 1)

        val starPrefix = if (exprIsArg && paramIsArray) "*" else ""

        val paramStr = when {
            // Argument is in the pre-vararg section
            argIndex < paramVarargIndex -> "${starPrefix}p$argIndex"

            // Argument is in the vararg section
            argIndex in paramVarargIndex until (paramVarargIndex + (params.size - paramVarargIndex - afterVararg)) -> {
                val varargIndexIndex = argIndex - paramVarargIndex
                val param = if (paramIsArray) {
                    if (argIndex == 0) {
                        "p$paramVarargIndex"
                    } else {
                        "slice(p$paramVarargIndex, $argIndex, $afterVararg)"
                    }
                } else {
                    "p$paramVarargIndex[$varargIndexIndex]"
                }
                starPrefix + param
            }

            // Argument is in the post-vararg section
            argIndex >= (paramVarargIndex + (params.size - paramVarargIndex - afterVararg)) -> {
                val afterVarargIndex = argIndex - (params.size - afterVararg)
                // TODO: Explicitly assign args to post-vararg params of funcons
                "${starPrefix}p${paramVarargIndex + 1 + afterVarargIndex}"
            }

            else -> throw IndexOutOfBoundsException()
        }

        return paramStr
    }

    override fun visitChildren(node: RuleNode): String {
        ic(node::class.simpleName)
        return super.visitChildren(node)
    }
}
