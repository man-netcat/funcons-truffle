package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class RewriteVisitor(
    definition: ParseTree,
    private val rootExpr: ParseTree,
    obj: Object,
    private val entities: Map<String, String>,
) : CBSBaseVisitor<String>() {
    private val callStack = ArrayDeque<String>()

    private val varargParamIndex: Int = obj.params.indexOfFirst { it.type.isVararg }
    private val args: MutableList<ExprContext> = mutableListOf()
    private val typeParams: MutableList<ExprContext> = mutableListOf()
    private val argsSize: Int
    private val paramsSize: Int
    private val nVarargArgs: Int
    private val paramsAfterVararg: Int
    private val argsAfterVararg: Int

    init {
        val (extractedArgs, extractedTypes) = extractArgs(definition)
        args.addAll(extractedArgs)
        typeParams.addAll(extractedTypes)
        argsSize = args.size
        paramsSize = obj.params.size
        nVarargArgs = argsSize - (paramsSize - 1)
        paramsAfterVararg = paramsSize - (varargParamIndex + 1)
        argsAfterVararg = argsSize - (varargParamIndex + nVarargArgs)
    }

    fun extractArgs(expr: ParseTree): Pair<List<ExprContext>, List<ExprContext>> {
        fun argHelper(args: List<ExprContext>): Pair<List<ExprContext>, List<ExprContext>> {
            val (values, types) = args.partition { arg -> arg !is TypeExpressionContext || arg.value != null }
            val argsWithValue = values.map { arg -> if (arg is TypeExpressionContext) arg.value else arg }
            val argsWithType = types.map { arg -> (arg as TypeExpressionContext).type }

            return argsWithValue to argsWithType
        }


        fun paramHelper(params: ParamsContext?): Pair<List<ExprContext>, List<ExprContext>> {
            if (params == null) return emptyList<ExprContext>() to emptyList()
            val (values, types) = params.param().partition { param -> param.value != null }
            val args = values.map { param -> param.value }
            val paramTypes = types.map { param -> param.type }

            return args to paramTypes
        }

        return when (expr) {
            is FunconDefinitionContext -> paramHelper(expr.params())
            is TypeDefinitionContext -> paramHelper(expr.params())
            is DatatypeDefinitionContext -> paramHelper(expr.params())

            is FunconExpressionContext -> {
                when (val args = expr.args()) {
                    is NoArgsContext -> emptyList<ExprContext>() to emptyList()
                    is SingleArgsContext -> argHelper(listOf(args.expr()))
                    is MultipleArgsContext -> argHelper(args.exprs().expr())
                    is ListIndexExpressionContext -> argHelper(args.indices.expr())
                    else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
                }
            }

            else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}, ${expr.text}")
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
        return if (exprs.isNullOrEmpty()) "EmptySequenceNode()" else "ListNode(${visit(tuple.exprs())})"
    }

    override fun visitListExpression(list: ListExpressionContext): String {
        val exprs = list.exprs()?.expr()
        return if (exprs.isNullOrEmpty()) "EmptyListNode()" else "ListNode(${visit(list.exprs())})"
    }

    override fun visitSetExpression(set: SetExpressionContext): String {
        val expr = set.expr()
        return if (expr == null) "EmptySetNode()" else "SetsNode(${visit(set.expr())})"
    }

    override fun visitMapExpression(map: MapExpressionContext): String = "MapsNode(${visitPairs(map.pairs())})"

    private fun visitSequences(nodes: List<ParseTree>, sep: String = ", "): String =
        nodes.joinToString(sep) { visit(it) }

    override fun visitExprs(exprs: ExprsContext): String = visitSequences(exprs.expr())

    override fun visitPairs(pairs: PairsContext): String = visitSequences(pairs.pair())

    override fun visitPair(pair: PairContext): String = "${visit(pair.key)} to ${visit(pair.value)}"

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): String =
        if (suffixExpr.text != "_?") when (suffixExpr.op.text) {
            "*", "+" -> makeParamStr(suffixExpr.text, argIsArray = true)
            "?" -> makeParamStr(suffixExpr.text) + "?"
            else -> throw DetailedException("Unexpected operator type: ${suffixExpr.op.text}")
        } else "null"

    override fun visitVariable(varExpr: VariableContext): String =
        if (varExpr.text != "_") makeParamStr(varExpr.text) else "null"

    override fun visitVariableStep(varStep: VariableStepContext): String {
        val stepSuffix = "p".repeat(varStep.squote().size)
        return makeParamStr(varStep.varname.text, stepSuffix = stepSuffix)
    }

    override fun visitNumber(num: NumberContext): String = "(${num.text}).toIntegersNode()"

    override fun visitString(string: StringContext): String = "(${string.text}).toStringsNode()"

    override fun visitTypeExpression(typeExpr: TypeExpressionContext): String = visit(typeExpr.value)

    fun makeParamStr(
        text: String,
        stepSuffix: String = "",
        argIsArray: Boolean = false,
        forcedArgIndex: Int = -1
    ): String {
        if (text in entities.keys) {
            val labelName = entities[text]
            return entityMap(labelName!!)
        }

        val exprIsArg = listOf(
            FunconExpressionContext::class, ListIndexExpressionContext::class, ListExpressionContext::class
        ).any { it.isInstance(rootExpr) }

        val argIndex = if (forcedArgIndex == -1) {
            args.indexOfFirst { ArgVisitor(text).visit(it) }
        } else forcedArgIndex

        if (argIndex == -1) throw StringNotFoundException(text, args.map { it.text })

        val (paramIndex, varargParamIndexed) = getParamIndex(argIndex)

        if (text == "()") return "p$paramIndex"

        val starPrefix = if (exprIsArg && argIsArray) "*" else ""

        val param = when {
            // If no vararg parameter, ignore this branch entirely
            varargParamIndex >= 0 && argIndex < varargParamIndex -> "p$paramIndex"

            // Handle vararg parameter if it exists
            varargParamIndex >= 0 && argIndex in varargParamIndex until varargParamIndex + nVarargArgs -> {
                if (!argIsArray) "p$paramIndex[$varargParamIndexed]"
                else if (argsAfterVararg > 0) "slice(p$paramIndex, $argIndex, $argsAfterVararg)"
                else if (argIndex != 0) "slice(p$paramIndex, $argIndex)"
                else "p$paramIndex"
            }

            // For arguments after vararg or if no vararg exists
            argIndex < argsSize -> "p$paramIndex"

            else -> throw IndexOutOfBoundsException()
        }

        return "$starPrefix$param$stepSuffix"
    }

    fun getParamIndex(argIndex: Int): Pair<Int, Int?> {
        return when {
            // Case when there is no vararg parameter (varargParamIndex == -1)
            varargParamIndex == -1 || argIndex < varargParamIndex -> Pair(argIndex, null)

            // Case for an actual vararg parameter range
            argIndex in varargParamIndex until varargParamIndex + nVarargArgs -> {
                val varargParamIndexed = argIndex - varargParamIndex
                Pair(varargParamIndex, varargParamIndexed)
            }

            // Case for parameters after the vararg parameter
            argIndex < argsSize -> {
                // Adjust argIndex based on the number of vararg arguments
                val paramIndex = argIndex - (nVarargArgs - 1)
                require(paramIndex >= 0) { "Calculated paramIndex is negative. Check nVarargArgs." }
                Pair(paramIndex, null)
            }

            else -> throw IndexOutOfBoundsException("argIndex $argIndex out of bounds.")
        }
    }
}
