package main

import cbs.CBSParser.*
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

        fun rewriteFunconExpr(ctx: ParseTree): String {
            val obj = getObject(ctx)
            val args = extractArgs(ctx)

            val argStrs = args.map { rewriteRecursive(it) }

            val nonSequence: List<String>
            val sequence: List<String>
            val postSequence: List<String>

            if (obj.sequenceIndex >= 0) {
                val postSequenceCount = obj.params.size - obj.sequenceIndex - 1
                nonSequence = argStrs.take(obj.sequenceIndex)
                postSequence = argStrs.takeLast(postSequenceCount)
                sequence = argStrs.drop(obj.sequenceIndex).dropLast(postSequenceCount)
            } else {
                nonSequence = argStrs
                sequence = emptyList()
                postSequence = emptyList()
            }

            val parts = mutableListOf<String>()
            if (nonSequence.isNotEmpty()) {
                parts.add(nonSequence.joinToString())
            }
            if (sequence.isNotEmpty()) {
                parts.add("SequenceNode(${sequence.joinToString()})")
            }
            if (postSequence.isNotEmpty()) {
                parts.add(postSequence.joinToString())
            }
            val argStr = parts.joinToString()

            val isNullArg = obj.hasNullable && args.isEmpty()
            val isEmptyArg = obj.params.size == 1 && obj.sequenceIndex == 0 && args.isEmpty()

            return if (isNullArg) "${obj.nodeName}(NullValueNode())"
            else if (isEmptyArg) "${obj.nodeName}(SequenceNode())"
            else "${obj.nodeName}($argStr)"
        }

        fun rewriteExpression(toRewrite: ParseTree): String {
            return when (toRewrite) {
                is FunconExpressionContext -> rewriteFunconExpr(toRewrite)
                is ListExpressionContext -> "Value" + rewriteFunconExpr(toRewrite)
                is SetExpressionContext -> "Value" + rewriteFunconExpr(toRewrite)
                is LabelContext -> rewriteFunconExpr(toRewrite)
                is MapExpressionContext -> {
                    val pairStr = toRewrite.pairs().pair().joinToString { pair -> rewriteRecursive(pair) }
                    "ValueMapNode(SequenceNode($pairStr))"
                }

                is TupleExpressionContext -> {
                    val pairStr = toRewrite.exprs()?.expr()?.joinToString { expr ->
                        rewriteRecursive(expr)
                    }
                    if (pairStr != null) "SequenceNode(${pairStr})" else "SequenceNode()"
                }

                is SuffixExpressionContext -> mapParamString(toRewrite.text)

                is VariableContext -> mapParamString(toRewrite.text)
                is NumberContext -> {
                    val nodeName = if ('-' in toRewrite.text) "IntegerNode" else "NaturalNumberNode"
                    "$nodeName(${toRewrite.text})"
                }

                is StringContext -> "StringNode(${toRewrite.text})"
                is TypeExpressionContext -> rewriteRecursive(toRewrite.value)
                is NestedExpressionContext -> rewriteRecursive(toRewrite.expr())
                is PairContext -> {
                    val key = rewriteRecursive(toRewrite.key)
                    val value = rewriteRecursive(toRewrite.value)
                    "TupleNode(SequenceNode($key, $value))"
                }

                else -> throw IllegalArgumentException("Unsupported context type: ${toRewrite::class.simpleName}, ${toRewrite.text}")
            }
        }

        return rewriteExpression(toRewrite)
    }

    return rewriteRecursive(toRewrite)
}

fun getParamStrs(definition: ParseTree, prefix: String = ""): List<RewriteData> {
    fun makeParamStr(
        argIndex: Int, argsSize: Int, obj: Object, parentStr: String, argIsSequence: Boolean = false,
    ): String {
        // Calculate the number of arguments passed to the vararg
        val nSequenceArgs = argsSize - (obj.params.size - 1)
        if (obj.sequenceIndex >= 0) argsSize - (obj.sequenceIndex + nSequenceArgs) else 0

        // Utility function to build parameter string based on provided condition
        fun buildParamString(paramIndex: Int, suffix: String = ""): String {
            val prefix = if (parentStr.isEmpty()) "l" else "p"
            return listOf(parentStr, "$prefix$paramIndex").filterNot { it.isEmpty() }.joinToString(".") + suffix
        }

        return when {
            // Case when there is no sequence (obj.sequenceIndex == -1)
            obj.sequenceIndex == -1 || argIndex in 0 until obj.sequenceIndex -> {
                buildParamString(argIndex)
            }

            // Case for an actual sequence range
            argIndex in obj.sequenceIndex until obj.sequenceIndex + nSequenceArgs -> {
                val sequenceRelativeIndex = argIndex - obj.sequenceIndex
                assert(sequenceRelativeIndex >= 0) { "Index out of bounds" }

                val base = buildParamString(obj.sequenceIndex)
                if (argIsSequence) {
                    when (sequenceRelativeIndex) {
                        0 -> base
                        1 -> "$base.tail"
                        else -> "$base.sliceFrom($sequenceRelativeIndex)"
                    }
                } else {
                    when (sequenceRelativeIndex) {
                        0 -> "$base.head"
                        1 -> "$base.second"
                        2 -> "$base.third"
                        3 -> "$base.fourth"
                        else -> throw IndexOutOfBoundsException("sequenceRelativeIndex $sequenceRelativeIndex out of bounds.")
                    }
                }
            }

            else -> throw IndexOutOfBoundsException("argIndex $argIndex out of bounds.")
        }
    }

    fun extractArgsRecursive(
        definition: ParseTree,
        parentStr: String = prefix,
    ): List<RewriteData> {
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
                    val newStr =
                        makeParamStr(argIndex, args.size, obj, parentStr, argIsSequence = argIsSequence)
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