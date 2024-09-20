package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class RewriteVisitor(
    private val rootExpr: ParseTree, private val params: List<Param>, private val ruleArgs: List<ExprContext?>
) : CBSBaseVisitor<String>() {
    private val callStack = ArrayDeque<String>()

    override fun visitFunconExpression(funcon: FunconExpressionContext): String {
        val name = funcon.name.text
        callStack.addLast(name)
        val argStr = when (val rewriteArgs = funcon.args()) {
            is MultipleArgsContext -> visit(rewriteArgs.exprs())
            is SingleArgsContext -> visit(rewriteArgs.expr())
            is NoArgsContext -> ""
            else -> throw DetailedException("Unexpected arg type: ${rewriteArgs::class.simpleName}")
        }
        callStack.removeLast()
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
        val (text, argIsArray, executeStr) = when (expr) {
            is SuffixExpressionContext -> Triple(expr.text, true, "")
            is VariableContext -> Triple(expr.varname.text, false, "")
            is VariableStepContext -> {
                var numSteps = expr.squote().size
                Triple(expr.varname.text, false, "p".repeat(numSteps))
            }

            else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}")
        }

        val exprIsArg = when (rootExpr) {
            is FunconExpressionContext, is ListIndexExpressionContext, is ListExpressionContext -> true
            else -> false
        }

        val argIndex = ruleArgs.indexOfFirst { ArgVisitor(text).visit(it) }
        if (argIndex == -1) {
            val stringArgs = ruleArgs.map { it?.text }
            throw DetailedException("String '$text' not found in $stringArgs")
        }
        val varargParamIndex = params.indexOfFirst { it.type.isVararg }
        val starPrefix = if (exprIsArg && argIsArray) "*" else ""
        if (varargParamIndex == -1) return "${starPrefix}p$argIndex$executeStr"

        val argsSize = ruleArgs.size
        val paramsSize = params.size
        val nVarargArgs = argsSize - (paramsSize - 1)
        val paramsAfterVararg = paramsSize - (varargParamIndex + 1)
        val argsAfterVararg = argsSize - (varargParamIndex + nVarargArgs)
        if (paramsAfterVararg != argsAfterVararg) {
            throw DetailedException("Unequal args for params after vararg")
        }

        return starPrefix + if (argIndex < varargParamIndex) {
            // Arg is pre-vararg param index
            val paramIndex = argIndex
            "p$paramIndex$executeStr"
        } else if (argIndex in varargParamIndex until varargParamIndex + nVarargArgs) {
            // Arg is vararg param index
            val paramIndex = varargParamIndex
            val varargParamIndexed = argIndex - varargParamIndex
            if (!argIsArray) {
                "p${paramIndex}$executeStr[$varargParamIndexed]"
            } else if (argsAfterVararg > 0) {
                "slice(p$paramIndex$executeStr, $argIndex, $argsAfterVararg)"
            } else if (argIndex != 0) {
                "slice(p$paramIndex$executeStr, $argIndex)"
            } else {
                "p$paramIndex$executeStr"
            }
        } else if (argIndex < argsSize) {
            println("post-vararg, num varargs: $nVarargArgs")
            // Arg is post-vararg param index
            val paramIndex = argIndex - (nVarargArgs - 1)
            "p$paramIndex$executeStr"
        } else {
            throw IndexOutOfBoundsException()
        }
    }

    override fun visitChildren(node: RuleNode): String {
        return super.visitChildren(node)
    }
}
