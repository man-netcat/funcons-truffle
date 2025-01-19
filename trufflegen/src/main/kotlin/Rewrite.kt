package main

import cbs.CBSParser
import main.dataclasses.Param
import main.dataclasses.RewriteData
import main.dataclasses.Type
import main.exceptions.DetailedException
import main.exceptions.StringNotFoundException
import main.objects.Object
import objects.FunconObject
import org.antlr.v4.runtime.tree.ParseTree

fun rewriteType(type: Type, nullable: Boolean = true): String {
    var nestedValue = 0
    fun rewriteTypeRecursive(toRewrite: CBSParser.ExprContext?, nullable: Boolean): String {
        return when (toRewrite) {
            is CBSParser.FunconExpressionContext -> toClassName(toRewrite.name.text)
            is CBSParser.ListExpressionContext -> toClassName("ListNode")
            is CBSParser.SetExpressionContext -> toClassName("SetNode")

            is CBSParser.SuffixExpressionContext -> {
                val baseType = rewriteTypeRecursive(toRewrite.expr(), nullable)
                when (val op = toRewrite.op.text) {
                    "?" -> if (nullable) "$baseType?" else baseType
                    "*", "+" -> if (type.isParam && nestedValue == 0) {
                        nestedValue++
                        rewriteTypeRecursive(toRewrite.expr(), nullable)
                    } else "Array<$baseType>"

                    else -> throw DetailedException("Unexpected operator: $op, full context: ${toRewrite.text}")
                }
            }

            is CBSParser.PowerExpressionContext -> {
                if (type.isParam && nestedValue == 0) {
                    nestedValue++
                    rewriteTypeRecursive(toRewrite.operand, nullable)
                } else {
                    "Array<${rewriteTypeRecursive(toRewrite.operand, nullable)}>"
                }
            }

            is CBSParser.TupleExpressionContext -> {
                if (toRewrite.exprs() == null) return "Unit"

                val tupleLength = toRewrite.exprs().expr().size
                val clsName = when (tupleLength) {
                    1 -> "Tuple1Node"
                    2 -> "Tuple2Node"
                    3 -> "TupleNode"
                    else -> throw DetailedException("Unexpected tuple length: $tupleLength")
                }
                val clsParams = toRewrite.exprs().expr().joinToString { rewriteTypeRecursive(it, nullable) }
                "$clsName<$clsParams>"
            }

            is CBSParser.BinaryComputesExpressionContext -> {
                "(" + rewriteTypeRecursive(toRewrite.lhs, nullable) + ") -> " + rewriteTypeRecursive(
                    toRewrite.rhs, nullable
                )
            }

            is CBSParser.OrExpressionContext -> {
                if (toRewrite.rhs.text == toRewrite.rhs.text) {
                    rewriteTypeRecursive(toRewrite.lhs, nullable) + "?"
                } else {
                    throw DetailedException("Unexpected return type: ${toRewrite.text}")
                }
            }

            is CBSParser.VariableContext -> toRewrite.varname.text + "p".repeat(toRewrite.squote().size)
            is CBSParser.NestedExpressionContext -> rewriteTypeRecursive(toRewrite.expr(), nullable)
            is CBSParser.UnaryComputesExpressionContext -> rewriteTypeRecursive(toRewrite.expr(), nullable)
            is CBSParser.NumberContext -> toRewrite.text
            is CBSParser.TypeExpressionContext -> rewriteTypeRecursive(toRewrite.type, nullable)
            is CBSParser.ComplementExpressionContext -> rewriteTypeRecursive(toRewrite.expr(), nullable)
            else -> throw DetailedException("Unsupported context type: ${toRewrite?.javaClass?.simpleName}")
        }
    }

    return rewriteTypeRecursive(type.expr, nullable)
}

fun rewrite(definition: ParseTree, toRewrite: ParseTree, rewriteData: List<RewriteData> = emptyList()): String {
    fun rewriteRecursive(toRewrite: ParseTree): String {
        fun mapParamString(def: ParseTree, str: String): String {
            val paramStrs = getParamStrs(def)
            val exprMap = (paramStrs + rewriteData).associate { (arg, _, paramStr) -> Pair(arg?.text, paramStr) }
            return exprMap[str] ?: throw StringNotFoundException(str, exprMap.keys.toList())
        }

        fun rewriteFunconExpr(name: String, context: ParseTree): String {
            val obj = globalObjects[name]!!
            val className = toClassName(name)
            val args = extractArgs(context)
            val argStr = if (!(obj.hasNullable && args.isEmpty())) {
                args.mapIndexed { i, arg ->
                    if (obj.paramsAfterVararg > 0 && i in args.size - obj.paramsAfterVararg until args.size) {
                        val paramIndex = obj.params.size - (args.size - i)
                        "p$paramIndex=${rewriteRecursive(arg)}"
                    } else rewriteRecursive(arg)
                }.joinToString()
            } else "null"
            val prefix = if (obj is FunconObject && obj.returns.isArray) "*" else ""
            val suffix = if (obj is FunconObject) ".execute(frame)" else ""
            return "$prefix$className($argStr)$suffix"
        }

        return when (toRewrite) {
            is CBSParser.FunconExpressionContext -> rewriteFunconExpr(toRewrite.name.text, toRewrite)
            is CBSParser.ListExpressionContext -> rewriteFunconExpr("list", toRewrite)
            is CBSParser.SetExpressionContext -> rewriteFunconExpr("set", toRewrite)
            is CBSParser.LabelContext -> rewriteFunconExpr(toRewrite.name.text, toRewrite)
            is CBSParser.MapExpressionContext -> {
                val pairStr = toRewrite.pairs().pair().joinToString { pair -> rewriteRecursive(pair) }
                "MapNode($pairStr)"
            }

            is CBSParser.TupleExpressionContext -> {
                val pairStr = toRewrite.exprs()?.expr()?.joinToString(", ") { expr ->
                    rewriteRecursive(expr)
                }
                if (pairStr != null) "arrayOf(${pairStr})" else "emptyArray()"
            }

            is CBSParser.SuffixExpressionContext -> {
                val suffixStr = if (toRewrite.op.text == "?") "?" else ""
                mapParamString(definition, toRewrite.text) + suffixStr
            }

            is CBSParser.VariableContext -> mapParamString(definition, toRewrite.text)
            is CBSParser.NumberContext -> {
                if ('-' in toRewrite.text) {
                    "(${toRewrite.text}).toIntegerNode()"
                } else {
                    "(${toRewrite.text}).toNaturalNumberNode()"
                }
            }

            is CBSParser.StringContext -> "(${toRewrite.text}).toStringNode()"
            is CBSParser.TypeExpressionContext -> rewriteRecursive(toRewrite.value)
            is CBSParser.NestedExpressionContext -> rewriteRecursive(toRewrite.expr())
            is CBSParser.PairContext -> {
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
    fun paramHelper(params: CBSParser.ParamsContext?): List<Param> =
        params?.param()?.mapIndexed { i, param -> Param(i, param.value, param.type) } ?: emptyList()

    return when (obj) {
        is CBSParser.FunconDefinitionContext -> paramHelper(obj.params())
        is CBSParser.TypeDefinitionContext -> paramHelper(obj.params())
        is CBSParser.DatatypeDefinitionContext -> paramHelper(obj.params())
        is CBSParser.ControlEntityDefinitionContext -> paramHelper(obj.params())
        is CBSParser.ContextualEntityDefinitionContext -> paramHelper(obj.params())
        is CBSParser.MutableEntityDefinitionContext -> paramHelper(obj.lhs)

        else -> throw DetailedException("Unexpected funcon type: ${obj::class.simpleName}, ${obj.text}")
    }
}

fun extractArgs(expr: ParseTree): List<CBSParser.ExprContext> {
    return when (expr) {
        is CBSParser.FunconExpressionContext -> makeArgList(expr.args())
        is CBSParser.ListExpressionContext -> expr.elements?.expr() ?: emptyList()
        is CBSParser.SetExpressionContext -> expr.elements?.expr() ?: emptyList()
        is CBSParser.LabelContext -> if (expr.value != null) listOf(expr.value) else emptyList()
        else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}, ${expr.text}")
    }
}

fun argsToParams(expr: ParseTree): List<Param> {
    val args = extractArgs(expr)
    return args.mapIndexed { i, arg ->
        when (arg) {
            is CBSParser.TypeExpressionContext -> Param(i, arg.value, arg.type)
            else -> Param(i, arg, null)
        }
    }
}

fun makeArgList(args: CBSParser.ArgsContext): List<CBSParser.ExprContext> {
    return when (args) {
        is CBSParser.NoArgsContext -> emptyList()
        is CBSParser.SingleArgsContext -> {
            if (args.expr() !is CBSParser.TupleExpressionContext) {
                listOf(args.expr())
            } else emptyList()
        }

        is CBSParser.MultipleArgsContext -> args.exprs()?.expr() ?: emptyList()
        else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
    }
}

fun partitionArrayArgs(args: List<CBSParser.ExprContext?>): Pair<List<CBSParser.ExprContext>, List<CBSParser.ExprContext>> {
    return args.filterNotNull().partition { arg ->
        arg is CBSParser.SuffixExpressionContext || (arg is CBSParser.TypeExpressionContext && arg.value is CBSParser.SuffixExpressionContext)
    }
}

fun getParamStrs(definition: ParseTree, isParam: Boolean = false, prefix: String = ""): List<RewriteData> {
    fun makeParamStr(
        argIndex: Int, argsSize: Int, obj: Object, parentStr: String, argIsArray: Boolean = false
    ): String {
        // Calculate the number of arguments passed to the vararg
        val nVarargArgs = argsSize - (obj.params.size - 1)
        val argsAfterVararg = if (obj.varargParamIndex >= 0) argsSize - (obj.varargParamIndex + nVarargArgs) else 0

        // Prefix '*' if the argument is an array
        val starPrefix = if (argIsArray && !isParam) "*" else ""

        // Utility function to build parameter string based on provided condition
        fun buildParamString(paramIndex: Int, suffix: String = ""): String {
            return listOf(parentStr, "p$paramIndex").filterNot { it.isEmpty() }.joinToString(".") + suffix
        }

        return when {
            // Case when there is no vararg parameter (obj.varargParamIndex == -1)
            obj.varargParamIndex == -1 || argIndex in 0 until obj.varargParamIndex -> {
                starPrefix + buildParamString(argIndex)
            }

            // Case for an actual vararg parameter range
            argIndex in obj.varargParamIndex until obj.varargParamIndex + nVarargArgs -> {
                val varargRelativeIndex = argIndex - obj.varargParamIndex

                if (!argIsArray) {
                    buildParamString(obj.varargParamIndex, "[$varargRelativeIndex]")
                } else if (varargRelativeIndex != 0) {
                    starPrefix + "slice(${buildParamString(obj.varargParamIndex)}, $varargRelativeIndex)"
                } else {
                    starPrefix + buildParamString(obj.varargParamIndex)
                }
            }

            // Case for parameters after the vararg parameter
            argIndex in obj.varargParamIndex + nVarargArgs until argsSize -> {
                "TODO(\"Params after vararg not implemented\")"
            }

            else -> throw IndexOutOfBoundsException("argIndex $argIndex out of bounds.")
        }
    }

    fun extractArgsRecursive(definition: ParseTree, parentStr: String = prefix): List<RewriteData> {
        val (obj, args) = when (definition) {
            is CBSParser.FunconDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
            is CBSParser.TypeDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
            is CBSParser.DatatypeDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
            is CBSParser.FunconExpressionContext -> globalObjects[definition.name.text]!! to argsToParams(definition)
            is CBSParser.ListExpressionContext -> globalObjects["list"]!! to argsToParams(definition)
            is CBSParser.SetExpressionContext -> globalObjects["set"]!! to argsToParams(definition)
            is CBSParser.LabelContext -> globalObjects[definition.name.text]!! to argsToParams(definition)
            else -> throw DetailedException("Unexpected definition type: ${definition::class.simpleName}, ${definition.text}")
        }

        return args.flatMapIndexed { argIndex, (arg, type) ->
            when (arg) {
                null -> {
                    // Argument is a type
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr)
                    listOf(RewriteData(type, null, newStr))
                }

                is CBSParser.FunconExpressionContext, is CBSParser.ListExpressionContext, is CBSParser.SetExpressionContext -> {
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr)
                    listOf(RewriteData(null, arg, newStr)) + extractArgsRecursive(arg, newStr)
                }

                is CBSParser.SuffixExpressionContext, is CBSParser.VariableContext, is CBSParser.NumberContext -> {
                    val argIsArray = (arg is CBSParser.SuffixExpressionContext && arg.op.text in listOf("+", "*"))
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr, argIsArray = argIsArray)
                    listOf(RewriteData(arg, type, newStr))
                }

                is CBSParser.TupleExpressionContext -> {
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr, argIsArray = true)
                    listOf(RewriteData(arg, null, newStr))
                }

                else -> throw DetailedException("Unexpected arg type: ${arg::class.simpleName}, ${arg.text}")
            }
        }
    }
    return extractArgsRecursive(definition)
}