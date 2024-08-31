package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.icecream.IceCream.ic
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class RewriteVisitor(private val params: List<Param>, private val ruleArgs: List<ExprContext?>) :
    CBSBaseVisitor<String>() {

    override fun visitFunconExpression(funcon: FunconExpressionContext): String {
        val name = funcon.name.text
        val argStr = when (val rewriteArgs = funcon.args()) {
            is MultipleArgsContext -> visit(rewriteArgs.exprs())
            is SingleArgsContext -> visit(rewriteArgs.expr())
            is NoArgsContext -> ""
            else -> throw Exception("Unexpected arg type: ${rewriteArgs::class.simpleName}")
        }
        val className = toClassName(name)
        return "$className($argStr)"
    }

    override fun visitTupleExpression(tuple: TupleExpressionContext): String {
        val exprs = tuple.exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "null" else "tuple(${visit(tuple.exprs())})"
    }

    override fun visitListExpression(list: ListExpressionContext): String {
        val exprs = list.exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "emptyList()" else "listOf(${visit(list.exprs())})"
    }

    override fun visitSetExpression(set: SetExpressionContext): String = "setOf(${visit(set.exprs())})"

    override fun visitMapExpression(map: MapExpressionContext): String = "hashMapOf(${visitPairs(map.pairs())})"

    private fun visitSequences(nodes: List<ParseTree>, sep: String = ", "): String =
        nodes.joinToString(sep) { visit(it) }

    override fun visitExprs(exprs: ExprsContext): String = visitSequences(exprs.expr())

    override fun visitPairs(pairs: PairsContext): String = visitSequences(pairs.pair())

    override fun visitPair(pair: PairContext): String = "${pair.key.text} to ${pair.value.text}"

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): String = rewriteExpr(suffixExpr)

    override fun visitVariable(varExpr: VariableContext): String = rewriteExpr(varExpr)

    override fun visitVariableStep(varStep: VariableStepContext): String = rewriteExpr(varStep) + ".execute(frame)"

    override fun visitNumber(num: NumberContext): String = num.text

    override fun visitString(string: StringContext): String = string.text

    private fun rewriteExpr(expr: ParseTree): String {
        val (text, argIsVararg) = when (expr) {
            is SuffixExpressionContext -> expr.text to true
            is VariableContext -> expr.varname.text to false
            is VariableStepContext -> expr.varname.text to false
            else -> throw Exception("Unexpected expression type: ${expr::class.simpleName}")
        }

        val argIndex = ruleArgs.indexOfFirst { ArgVisitor(text).visit(it) == true }
        if (argIndex == -1) {
            val stringArgs = ruleArgs.map { it?.text }
            throw Exception("String '$text' not found in $stringArgs")
        }

        val paramVarargIndex = params.indexOfFirst { it.type.isVararg }

        val afterVararg = params.size - (paramVarargIndex + 1)

        return argToParam(paramVarargIndex, afterVararg, argIndex, argIsVararg)
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

    override fun visitChildren(node: RuleNode): String {
        ic(node::class.simpleName)
        return super.visitChildren(node)
    }
}
