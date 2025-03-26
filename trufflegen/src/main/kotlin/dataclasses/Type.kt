package main.dataclasses

import cbs.CBSParser.*
import main.exceptions.DetailedException
import main.extractArgs
import main.toClassName

class Type(private val expr: ExprContext?, val isParam: Boolean = false) {
    var computes = false
    var isComplement = false
    var isStarExpr = false
    var isPlusExpr = false
    var isPower = false
    var isOptional = false
    var isNullable = false
    var isSequence = false
    var isVararg = false

    private fun handleUnaryComputesExpression(expr: UnaryComputesExpressionContext) {
        computes = true
        if (expr.operand is SuffixExpressionContext) {
            isVararg = true
        }
    }

    init {
        if (expr != null) {
            when (expr) {
                is SuffixExpressionContext -> {
                    val op = expr.op.text
                    if (isParam) isSequence = true else isVararg = true
                    when (op) {
                        "*" -> isStarExpr = true
                        "+" -> isPlusExpr = true
                        "?" -> isOptional = true
                    }
                    val operand = expr.operand
                    if (operand is NestedExpressionContext) {
                        val nestedExpr = operand.expr()
                        if (nestedExpr is UnaryComputesExpressionContext) handleUnaryComputesExpression(nestedExpr)
                    }
                }

                is PowerExpressionContext -> {
                    isPower = true
                    if (isParam) isSequence = true else isVararg = true
                    val operand = expr.operand
                    if (operand is NestedExpressionContext) {
                        val nestedExpr = operand.expr()
                        if (nestedExpr is UnaryComputesExpressionContext) handleUnaryComputesExpression(nestedExpr)
                    }
                }

                is UnaryComputesExpressionContext -> handleUnaryComputesExpression(expr)
                is ComplementExpressionContext -> isComplement = true
                is OrExpressionContext -> isNullable = true
            }

            if (isSequence && !isParam) throw IllegalStateException("A non-parameter type cannot be vararg")
        }
    }

    override fun toString(): String = expr?.text ?: "null"

    fun rewrite(full: Boolean = false): String {
        var isArrayCounter = 0
        fun rewriteTypeRecursive(toRewrite: ExprContext?): String {
            return when (toRewrite) {
                is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext -> {
                    val args = extractArgs(toRewrite)

                    val argStr = if (args.isNotEmpty() && full) {
                        "<" + args.joinToString { arg ->
                            rewriteTypeRecursive(arg)
                        } + ">"
                    } else ""
                    when (toRewrite) {
                        is FunconExpressionContext -> "${toClassName(toRewrite.name.text)}$argStr"
                        is ListExpressionContext -> "Value${toClassName("list")}$argStr"
                        is SetExpressionContext -> "Value${toClassName("set")}$argStr"
                        else -> throw DetailedException("Unsupported context type: ${toRewrite::class.simpleName}, with text: $toRewrite")
                    }
                }

                is SuffixExpressionContext -> {
                    isArrayCounter++
                    rewriteTypeRecursive(toRewrite.expr())
                }

                is PowerExpressionContext -> {
                    isArrayCounter++
                    rewriteTypeRecursive(toRewrite.operand)
                }

                is TupleExpressionContext -> {
                    if (toRewrite.exprs() == null) return "Unit"

                    val clsName = when (val tupleLength = toRewrite.exprs().expr().size) {
                        1 -> "Tuple1Node"
                        2 -> "Tuple2Node"
                        3 -> "TupleNode"
                        else -> throw DetailedException("Unexpected tuple length: $tupleLength")
                    }
                    val clsParams = toRewrite.exprs().expr().joinToString { rewriteTypeRecursive(it) }
                    "$clsName<$clsParams>"
                }

                is BinaryComputesExpressionContext -> {
                    "(" + rewriteTypeRecursive(toRewrite.lhs) + ") -> " + rewriteTypeRecursive(
                        toRewrite.rhs
                    )
                }

                is OrExpressionContext -> {
                    if (toRewrite.rhs.text == toRewrite.rhs.text) {
                        rewriteTypeRecursive(toRewrite.lhs)
                    } else {
                        throw DetailedException("Unexpected return type: ${toRewrite.text}")
                    }
                }

                is VariableContext -> if (toRewrite.text != "_") {
                    toRewrite.varname.text + "p".repeat(toRewrite.squote().size)
                } else "*"

                is NestedExpressionContext -> rewriteTypeRecursive(toRewrite.expr())
                is UnaryComputesExpressionContext -> rewriteTypeRecursive(toRewrite.expr())
                is NumberContext -> toRewrite.text
                is TypeExpressionContext -> rewriteTypeRecursive(toRewrite.type)
                is ComplementExpressionContext -> rewriteTypeRecursive(toRewrite.expr())
                else -> throw DetailedException("Unsupported context type: ${toRewrite?.javaClass?.simpleName}")
            }
        }

        val rewrite = rewriteTypeRecursive(expr)
        return if (isArrayCounter == 2 && isParam) {
            "Sequence<in $rewrite>"
        } else if (isArrayCounter == 1 && !isParam) {
            "Sequence<out $rewrite>"
        } else rewrite
    }
}
