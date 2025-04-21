package main

import cbs.CBSParser.*
import main.dataclasses.RewriteData
import main.exceptions.DetailedException
import main.exceptions.StringNotFoundException
import main.objects.Object
import org.antlr.v4.runtime.tree.ParseTree

fun rewrite(
    definition: ParseTree,
    toRewrite: ParseTree,
    rewriteData: List<RewriteData> = emptyList(),
    deepCopy: Boolean = false,
): String {
    fun rewriteRecursive(toRewrite: ParseTree): String {
        fun mapParamString(str: String): String {
            val paramStrs = getParamStrs(definition, deepCopy = deepCopy)
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
            if (nonSequence.isNotEmpty()) parts.add(nonSequence.joinToString())
            if (sequence.isNotEmpty()) parts.add("SequenceNode(${sequence.joinToString()})")
            if (postSequence.isNotEmpty()) parts.add(postSequence.joinToString())
            val argStr = parts.joinToString()

            return "${obj.nodeName}($argStr)"
        }

        return when (toRewrite) {
            is FunconExpressionContext,
            is ListExpressionContext,
            is SetExpressionContext,
            is LabelContext,
                -> rewriteFunconExpr(toRewrite)

            is MapExpressionContext -> {
                val pairStr = toRewrite.pairs().pair().joinToString { pair -> rewriteRecursive(pair) }
                "MapNode(SequenceNode($pairStr))"
            }

            is SequenceExpressionContext -> {
                val pairStr = toRewrite.exprs()?.expr()?.joinToString { expr ->
                    rewriteRecursive(expr)
                }
                if (pairStr != null) "SequenceNode(${pairStr})" else "SequenceNode()"
            }

            is SuffixExpressionContext -> {
                if (toRewrite.expr() is FunconExpressionContext) rewriteFunconExpr(toRewrite.expr())
                else mapParamString(toRewrite.text)
            }

            is VariableContext -> mapParamString(toRewrite.text)
            is NumberContext -> "IntegerNode(${toRewrite.text})"
            is StringContext -> {
                val charSequence = "toCharSequence(${toRewrite.text})"
                "StringNode(${charSequence})"
            }

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

    return rewriteRecursive(toRewrite)
}

fun getParamStrs(
    definition: ParseTree,
    prefix: String = "",
    deepCopy: Boolean = false,
): List<RewriteData> {
    fun makeParamStr(
        argIndex: Int,
        argsSize: Int,
        obj: Object,
        parentStr: String,
        argIsSequence: Boolean = false,
    ): String {
        // Calculate the number of arguments passed to the sequence
        val nSequenceArgs = argsSize - (obj.params.size - 1)
        if (obj.sequenceIndex >= 0) argsSize - (obj.sequenceIndex + nSequenceArgs) else 0

        fun getSuffix(sequenceRelativeIndex: Int, nSequenceArgs: Int): String {
            return if (argIsSequence) {
                if (sequenceRelativeIndex == nSequenceArgs - 1) {
                    when (sequenceRelativeIndex) {
                        0 -> ""
                        1 -> ".tail"
                        else -> ".sliceFrom($sequenceRelativeIndex)"
                    }
                } else ".init"
            } else {
                if (sequenceRelativeIndex != nSequenceArgs - 1) {
                    when (sequenceRelativeIndex) {
                        0 -> ".head"
                        1 -> ".second"
                        2 -> ".third"
                        3 -> ".fourth"
                        else -> ".get($sequenceRelativeIndex)"
                    }
                } else ".last"
            }
        }

        // Utility function to build parameter string based on provided condition
        fun buildParamString(paramIndex: Int, computes: Boolean, suffix: String = ""): String {
            val deepCopy = if (computes && deepCopy) ".deepCopy()" else ""
            return listOf(parentStr, "get($paramIndex)").filterNot { it.isEmpty() }
                .joinToString(".") + suffix + deepCopy
        }

        return when {
            // Case when there is no sequence (obj.sequenceIndex == -1)
            obj.sequenceIndex == -1 || argIndex in 0 until obj.sequenceIndex -> {
                buildParamString(argIndex, obj.params[argIndex].type.computes)
            }

            // Case for an actual sequence range
            argIndex in obj.sequenceIndex until obj.sequenceIndex + nSequenceArgs -> {
                val sequenceRelativeIndex = argIndex - obj.sequenceIndex
                assert(sequenceRelativeIndex >= 0) { "Index out of bounds" }

                val suffix = getSuffix(sequenceRelativeIndex, nSequenceArgs)
                buildParamString(obj.sequenceIndex, obj.sequenceParam!!.type.computes, suffix)
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
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr)
                    listOf(RewriteData(type, null, newStr))
                }

                is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext -> {
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr)

                    val sizeCondition = makeSizeCondition(arg, newStr)

                    val funconArgs = extractArgsRecursive(arg, newStr)
                    listOf(RewriteData(null, arg, newStr, sizeCondition = sizeCondition)) + funconArgs
                }

                is SuffixExpressionContext, is VariableContext, is NumberContext -> {
                    val argIsSequence = (arg is SuffixExpressionContext && arg.op.text in listOf("+", "*"))
                    val newStr =
                        makeParamStr(argIndex, args.size, obj, parentStr, argIsSequence = argIsSequence)
                    listOf(RewriteData(arg, type, newStr))
                }

                is SequenceExpressionContext -> {
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr, argIsSequence = true)

                    listOf(RewriteData(arg, null, newStr, sizeCondition = "${newStr}.isEmpty()" to 0))
                }

                else -> throw DetailedException("Unexpected arg type: ${arg::class.simpleName}, ${arg.text}")
            }
        }
    }
    return extractArgsRecursive(definition)
}