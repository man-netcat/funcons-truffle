package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class RewriteVisitor(
    private val rootExpr: ParseTree,
    params: List<Param>,
    private val ruleArgs: List<ExprContext>,
    private val entities: Map<String, String>,
) : CBSBaseVisitor<String>() {
    private val callStack = ArrayDeque<String>()

    private val varargParamIndex: Int = params.indexOfFirst { it.type.isVararg }
    private val argsSize: Int = ruleArgs.size
    private val paramsSize: Int = params.size
    private val nVarargArgs: Int = argsSize - (paramsSize - 1)
    private val paramsAfterVararg: Int = paramsSize - (varargParamIndex + 1)
    private val argsAfterVararg: Int = argsSize - (varargParamIndex + nVarargArgs)

    init {
        if (paramsAfterVararg != argsAfterVararg) {
            throw DetailedException("Unequal args for params after vararg")
        }
    }

    override fun visitFunconExpression(funcon: FunconExpressionContext): String {
        val name = funcon.name.text
        callStack.addLast(name)
        val argStr = when (val rewriteArgs = funcon.args()) {
            is MultipleArgsContext -> visit(rewriteArgs.exprs())
            is ListIndexExpressionContext -> visit(rewriteArgs.exprs())
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
        val expr = set.expr()
        return if (expr == null) "emptySet()" else "setOf(${visit(set.expr())})"
    }

    override fun visitMapExpression(map: MapExpressionContext): String = "hashMapOf(${visitPairs(map.pairs())})"

    private fun visitSequences(nodes: List<ParseTree>, sep: String = ", "): String =
        nodes.joinToString(sep) { visit(it) }

    override fun visitExprs(exprs: ExprsContext): String = visitSequences(exprs.expr())

    override fun visitPairs(pairs: PairsContext): String = visitSequences(pairs.pair())

    override fun visitPair(pair: PairContext): String = "${visit(pair.key)} to ${visit(pair.value)}"

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): String =
        makeParamStr(suffixExpr.text, argIsArray = true)

    override fun visitVariable(varExpr: VariableContext): String =
        if (varExpr.text != "_") makeParamStr(varExpr.text) else "null"

    override fun visitVariableStep(varStep: VariableStepContext): String {
        val stepSuffix = "p".repeat(varStep.squote().size)
        return makeParamStr(varStep.varname.text, stepSuffix = stepSuffix)
    }

    override fun visitNumber(num: NumberContext): String = num.text

    override fun visitString(string: StringContext): String = string.text

    override fun visitTypeExpression(typeExpr: TypeExpressionContext): String = visit(typeExpr.value)

    fun makeParamStr(
        text: String, stepSuffix: String = "", argIsArray: Boolean = false, forcedArgIndex: Int = -1
    ): String {
        // TODO: Fix parameter comparisons

        if (text in entities.keys) {
            val labelName = entities[text]
            return entityMap(labelName!!)
        }

        val exprIsArg = listOf(
            FunconExpressionContext::class, ListIndexExpressionContext::class, ListExpressionContext::class
        ).any { it.isInstance(rootExpr) }

        val argIndex = if (forcedArgIndex == -1) {
            ruleArgs.indexOfFirst { ArgVisitor(text).visit(it) }
        } else forcedArgIndex

        if (argIndex == -1) throw StringNotFoundException(text, ruleArgs.map { it.text })

        val (paramIndex, varargParamIndexed) = getParamIndex(argIndex)

        if (text == "()") return "p$paramIndex"

        val starPrefix = if (exprIsArg && argIsArray) "*" else ""

        if (varargParamIndex == -1) return "${starPrefix}p$argIndex"

        val param = when {
            argIndex < varargParamIndex -> "p$paramIndex"
            argIndex in varargParamIndex until varargParamIndex + nVarargArgs -> {
                if (!argIsArray) "p$paramIndex[$varargParamIndexed]"
                else if (argsAfterVararg > 0) "slice(p$paramIndex, $argIndex, $argsAfterVararg)"
                else if (argIndex != 0) "slice(p$paramIndex, $argIndex)"
                else "p$paramIndex"
            }

            argIndex < argsSize -> "p$paramIndex"
            else -> throw IndexOutOfBoundsException()
        }

        return "$starPrefix$param$stepSuffix"
    }

    fun getParamIndex(argIndex: Int): Pair<Int, Int?> {
        return when {
            argIndex < varargParamIndex -> Pair(argIndex, null)
            argIndex in varargParamIndex until varargParamIndex + nVarargArgs -> {
                val varargParamIndexed = argIndex - varargParamIndex
                Pair(varargParamIndex, varargParamIndexed)
            }

            argIndex < argsSize -> {
                val paramIndex = argIndex - (nVarargArgs - 1)
                Pair(paramIndex, null)
            }

            else -> throw IndexOutOfBoundsException()
        }
    }
}
