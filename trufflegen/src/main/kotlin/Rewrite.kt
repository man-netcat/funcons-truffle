package main

import cbs.CBSParser.*
import main.dataclasses.Param
import main.dataclasses.RewriteData
import main.exceptions.DetailedException
import main.exceptions.StringNotFoundException
import main.objects.Object
import org.antlr.v4.runtime.tree.ParseTree

fun rewrite(definition: ParseTree, toRewrite: ParseTree, rewriteData: List<RewriteData> = emptyList()): String {
    fun rewriteRecursive(toRewrite: ParseTree): String {
        fun mapParamString(str: String): String {
            val paramStrs = getParamStrs(definition)
            val exprMap = (paramStrs + rewriteData).associate { (arg, _, paramStr) -> Pair(arg?.text, paramStr) }
            return exprMap[str] ?: throw StringNotFoundException(str, exprMap.keys.toList())
        }

        fun rewriteFunconExpr(name: String, context: ParseTree): String {
            val obj = globalObjects[name]!!
            val className = toClassName(name)
            val args = extractArgs(context)
            val argStr = if (!(obj.hasNullable && args.isEmpty())) {
                args.joinToString { arg -> rewriteRecursive(arg) }
            } else "null"
            val str = "$className($argStr)"
            return str
        }

        return when (toRewrite) {
            is FunconExpressionContext -> rewriteFunconExpr(toRewrite.name.text, toRewrite)
            is ListExpressionContext -> rewriteFunconExpr("list", toRewrite)
            is SetExpressionContext -> rewriteFunconExpr("set", toRewrite)
            is LabelContext -> rewriteFunconExpr(toRewrite.name.text, toRewrite)
            is MapExpressionContext -> {
                val pairStr = toRewrite.pairs().pair().joinToString { pair -> rewriteRecursive(pair) }
                "MapNode($pairStr)"
            }

            is TupleExpressionContext -> {
                val pairStr = toRewrite.exprs()?.expr()?.joinToString(", ") { expr ->
                    rewriteRecursive(expr)
                }
                if (pairStr != null) "sequenceOf(${pairStr})" else "emptySequence()"
            }

            is SuffixExpressionContext -> {
                val nullableStr = if (toRewrite.op.text == "?") "?" else ""
                mapParamString(toRewrite.text) + nullableStr
            }

            is VariableContext -> mapParamString(toRewrite.text)
            is NumberContext -> {
                if ('-' in toRewrite.text) {
                    "newIntegerNode(${toRewrite.text})"
                } else {
                    "newNaturalNumberNode(${toRewrite.text})"
                }
            }

            is StringContext -> "newStringNode(${toRewrite.text})"
            is TypeExpressionContext -> rewriteRecursive(toRewrite.value)
            is NestedExpressionContext -> rewriteRecursive(toRewrite.expr())
            is PairContext -> {
                val key = rewriteRecursive(toRewrite.key)
                val value = rewriteRecursive(toRewrite.value)
                "TupleNode($key, $value)"
            }

            else -> throw IllegalArgumentException("Unsupported context type: ${toRewrite::class.simpleName}, ${toRewrite.text}")
        }
    }

    return rewriteRecursive(toRewrite)
}

fun extractParams(obj: ParseTree): List<Param> {
    fun paramHelper(params: ParamsContext?): List<Param> =
        params?.param()?.mapIndexed { i, param -> Param(i, param.value, param.type) } ?: emptyList()

    return when (obj) {
        is FunconDefinitionContext -> paramHelper(obj.params())
        is TypeDefinitionContext -> paramHelper(obj.params())
        is DatatypeDefinitionContext -> paramHelper(obj.params())
        is ControlEntityDefinitionContext -> paramHelper(obj.params())
        is ContextualEntityDefinitionContext -> paramHelper(obj.params())
        is MutableEntityDefinitionContext -> paramHelper(obj.lhs)

        else -> throw DetailedException("Unexpected funcon type: ${obj::class.simpleName}, ${obj.text}")
    }
}

fun extractArgs(expr: ParseTree): List<ExprContext> {
    return when (expr) {
        is FunconExpressionContext -> makeArgList(expr.args())
        is ListExpressionContext -> expr.elements?.expr() ?: emptyList()
        is SetExpressionContext -> expr.elements?.expr() ?: emptyList()
        is LabelContext -> if (expr.value != null) listOf(expr.value) else emptyList()
        else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}, ${expr.text}")
    }
}

fun argsToParams(expr: ParseTree): List<Param> {
    val args = extractArgs(expr)
    return args.mapIndexed { i, arg ->
        when (arg) {
            is TypeExpressionContext -> Param(i, arg.value, arg.type)
            else -> Param(i, arg, null)
        }
    }
}

fun makeArgList(args: ArgsContext): List<ExprContext> {
    return when (args) {
        is NoArgsContext -> emptyList()
        is SingleArgsContext -> {
            if (args.expr() !is TupleExpressionContext) {
                listOf(args.expr())
            } else emptyList()
        }

        is MultipleArgsContext -> args.exprs()?.expr() ?: emptyList()
        else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
    }
}

fun partitionArrayArgs(args: List<ExprContext?>): Pair<List<ExprContext>, List<ExprContext>> {
    return args.filterNotNull().partition { arg ->
        arg is SuffixExpressionContext || (arg is TypeExpressionContext && arg.value is SuffixExpressionContext)
    }
}

fun getParams(definition: ParseTree) = when (definition) {
    is FunconDefinitionContext,
    is TypeDefinitionContext,
    is ControlEntityDefinitionContext,
    is ContextualEntityDefinitionContext,
    is MutableEntityDefinitionContext,
    is DatatypeDefinitionContext,
        -> extractParams(definition)

    is FunconExpressionContext,
    is ListExpressionContext,
    is SetExpressionContext,
    is LabelContext,
        -> argsToParams(definition)

    else -> throw DetailedException("Unexpected definition type: ${definition::class.simpleName}, ${definition.text}")
}

fun getObject(definition: ParseTree): Object {
    val name = when (definition) {
        is FunconDefinitionContext -> definition.name.text
        is TypeDefinitionContext -> definition.name.text
        is DatatypeDefinitionContext -> definition.name.text
        is FunconExpressionContext -> definition.name.text
        is ListExpressionContext -> "list"
        is SetExpressionContext -> "set"
        is LabelContext -> definition.name.text
        else -> throw DetailedException("Unexpected definition type: ${definition::class.simpleName}, ${definition.text}")
    }
    return globalObjects[name]!!
}

fun getParamStrs(definition: ParseTree, prefix: String = ""): List<RewriteData> {
    fun makeParamStr(
        argIndex: Int, argsSize: Int, obj: Object, parentStr: String, argIsSequence: Boolean = false,
    ): String {
        // Calculate the number of arguments passed to the vararg
        val nVarargArgs = argsSize - (obj.params.size - 1)
        val argsAfterVararg = if (obj.sequenceIndex >= 0) argsSize - (obj.sequenceIndex + nVarargArgs) else 0

        // Utility function to build parameter string based on provided condition
        fun buildParamString(paramIndex: Int, suffix: String = ""): String {
            return listOf(parentStr, "p$paramIndex").filterNot { it.isEmpty() }.joinToString(".") + suffix
        }

        return when {
            // Case when there is no vararg parameter (obj.varargParamIndex == -1)
            obj.sequenceIndex == -1 || argIndex in 0 until obj.sequenceIndex -> {
                buildParamString(argIndex)
            }

            // Case for an actual vararg parameter range
            argIndex in obj.sequenceIndex until obj.sequenceIndex + nVarargArgs -> {
                val varargRelativeIndex = argIndex - obj.sequenceIndex

                if (!argIsSequence) {
                    buildParamString(obj.sequenceIndex, "[$varargRelativeIndex]")
                } else if (varargRelativeIndex == 1) {
                    "${buildParamString(obj.sequenceIndex)}.tail"
                } else if (varargRelativeIndex > 1) {
                    "${buildParamString(obj.sequenceIndex)}.sliceFrom($varargRelativeIndex)"
                } else {
                    buildParamString(obj.sequenceIndex)
                }
            }

            else -> throw IndexOutOfBoundsException("argIndex $argIndex out of bounds.")
        }
    }

    fun extractArgsRecursive(definition: ParseTree, parentStr: String = prefix): List<RewriteData> {
        val obj = getObject(definition)
        val args = getParams(definition)

        return args.flatMapIndexed { argIndex, (arg, type) ->
            when (arg) {
                null -> {
                    // Argument is a type
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr)
                    listOf(RewriteData(type, null, newStr))
                }

                is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext -> {
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr)
                    val funconArgs = extractArgsRecursive(arg, newStr)
                    listOf(RewriteData(null, arg, newStr)) + funconArgs.ifEmpty {
                        val funconObject = getObject(arg)
                        if (funconObject.params.isNotEmpty()) {
                            listOf(RewriteData(null, null, "$newStr.p0"))
                        } else listOf()
                    }
                }

                is SuffixExpressionContext, is VariableContext, is NumberContext -> {
                    val argIsSequence = (arg is SuffixExpressionContext && arg.op.text in listOf("+", "*"))
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr, argIsSequence = argIsSequence)
                    listOf(RewriteData(arg, type, newStr))
                }

                is TupleExpressionContext -> {
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr, argIsSequence = true)
                    listOf(RewriteData(arg, null, newStr))
                }

                else -> throw DetailedException("Unexpected arg type: ${arg::class.simpleName}, ${arg.text}")
            }
        }
    }
    return extractArgsRecursive(definition)
}