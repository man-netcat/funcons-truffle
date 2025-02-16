package main.dataclasses

import cbs.CBSParser.*
import main.exceptions.DetailedException
import main.extractArgs
import main.toClassName

class
Type(private val expr: ExprContext?, val isParam: Boolean = false) {
    var computes = false
    var isComplement = false
    var isStarExpr = false
    var isPlusExpr = false
    var isPower = false
    var isNullable = false
    var isVararg = false
    var isArray = false

    private fun handleUnaryComputesExpression(expr: UnaryComputesExpressionContext) {
        computes = true
        val unaryComputesExpression = expr.operand
        if (unaryComputesExpression is SuffixExpressionContext) {
            if (unaryComputesExpression.op.text == "?") {
                isNullable = true
            }
            isArray = true
        }
    }

    init {
        if (expr != null) {
            when (expr) {
                is SuffixExpressionContext -> {
                    val op = expr.op.text
                    when (op) {
                        "*" -> {
                            if (isParam) isVararg = true else isArray = true
                            isStarExpr = true
                        }

                        "+" -> {
                            if (isParam) isVararg = true else isArray = true
                            isPlusExpr = true
                        }

                        "?" -> isNullable = true
                    }
                    val operand = expr.operand
                    if (operand is NestedExpressionContext) {
                        val nestedExpr = operand.expr()
                        if (nestedExpr is UnaryComputesExpressionContext) handleUnaryComputesExpression(nestedExpr)
                    }
                }

                is PowerExpressionContext -> {
                    isPower = true
                    if (isParam) isVararg = true else isArray = true
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

            if (isVararg && !isParam) throw IllegalStateException("A non-parameter type cannot be vararg")
        }
    }

    override fun toString(): String = expr?.text ?: "null"

    fun rewrite(
        inNullableExpr: Boolean = false,
        full: Boolean = false,
    ): String {
        var isArrayCounter = 0
        fun rewriteTypeRecursive(toRewrite: ExprContext?, isNullable: Boolean): String {
            return when (toRewrite) {
                is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext -> {
                    val args = extractArgs(toRewrite)

                    val argStr = if (args.isNotEmpty() && full) {
                        "<" + args.joinToString { arg ->
                            rewriteTypeRecursive(arg, isNullable = true)
                        } + ">"
                    } else ""
                    when (toRewrite) {
                        is FunconExpressionContext -> "${toClassName(toRewrite.name.text)}$argStr"
                        is ListExpressionContext -> "${toClassName("list")}$argStr"
                        is SetExpressionContext -> "${toClassName("set")}$argStr"
                        else -> throw DetailedException("Unsupported context type: ${toRewrite::class.simpleName}, with text: $toRewrite")
                    }
                }

                is SuffixExpressionContext -> {
                    val baseType = rewriteTypeRecursive(toRewrite.expr(), isNullable)
                    when (val op = toRewrite.op.text) {
                        "?" -> if (isNullable) "$baseType?" else baseType
                        "*", "+" -> {
                            isArrayCounter++
                            baseType
                        }

                        else -> throw DetailedException("Unexpected operator: $op, full context: ${toRewrite.text}")
                    }
                }

                is PowerExpressionContext -> {
                    isArrayCounter++
                    rewriteTypeRecursive(toRewrite.operand, isNullable)
                }

                is TupleExpressionContext -> {
                    if (toRewrite.exprs() == null) return "Unit"

                    val clsName = when (val tupleLength = toRewrite.exprs().expr().size) {
                        1 -> "Tuple1Node"
                        2 -> "Tuple2Node"
                        3 -> "TupleNode"
                        else -> throw DetailedException("Unexpected tuple length: $tupleLength")
                    }
                    val clsParams = toRewrite.exprs().expr().joinToString { rewriteTypeRecursive(it, isNullable) }
                    "$clsName<$clsParams>"
                }

                is BinaryComputesExpressionContext -> {
                    "(" + rewriteTypeRecursive(toRewrite.lhs, isNullable) + ") -> " + rewriteTypeRecursive(
                        toRewrite.rhs, isNullable
                    )
                }

                is OrExpressionContext -> {
                    if (toRewrite.rhs.text == toRewrite.rhs.text) {
                        rewriteTypeRecursive(toRewrite.lhs, isNullable) + "?"
                    } else {
                        throw DetailedException("Unexpected return type: ${toRewrite.text}")
                    }
                }

                is VariableContext -> if (toRewrite.text != "_") {
                    toRewrite.varname.text + "p".repeat(toRewrite.squote().size)
                } else "*"

                is NestedExpressionContext -> rewriteTypeRecursive(toRewrite.expr(), isNullable)
                is UnaryComputesExpressionContext -> rewriteTypeRecursive(toRewrite.expr(), isNullable)
                is NumberContext -> toRewrite.text
                is TypeExpressionContext -> rewriteTypeRecursive(toRewrite.type, isNullable)
                is ComplementExpressionContext -> rewriteTypeRecursive(toRewrite.expr(), isNullable)
                else -> throw DetailedException("Unsupported context type: ${toRewrite?.javaClass?.simpleName}")
            }
        }

        val rewrite = rewriteTypeRecursive(expr, inNullableExpr)
        return if (isArrayCounter == 2 && isParam) {
            "Array<in $rewrite>"
        } else if (isArrayCounter == 1 && !isParam) {
            "Array<out $rewrite>"
        } else rewrite
//        return if (inNullableExpr && isNullable) "$res?" else res
    }
}
