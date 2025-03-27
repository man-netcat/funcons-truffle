package main.dataclasses

import cbs.CBSParser.*
import main.exceptions.DetailedException
import main.getObject
import main.objects.TypeObject
import objects.DatatypeFunconObject

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

    fun rewrite(): String {
        fun rewriteTypeRecursive(toRewrite: ExprContext?): String {
            fun rewriteFuncon(toRewrite: ExprContext): String {
                val obj = getObject(toRewrite)
                val prefix = if (obj.params.isNotEmpty()) when (obj) {
                    is DatatypeFunconObject, is TypeObject -> "Value"
                    else -> ""
                } else ""
                return "$prefix${obj.nodeName}"
            }

            return when (toRewrite!!) {
                is FunconExpressionContext,
                is ListExpressionContext,
                is SetExpressionContext,
                    -> rewriteFuncon(toRewrite)

                is TypeExpressionContext -> rewriteTypeRecursive(toRewrite.type)
                is ComplementExpressionContext -> rewriteTypeRecursive(toRewrite.expr())
                else -> throw DetailedException("Unsupported context type: ${toRewrite::class.simpleName}, with text: $toRewrite")
            }
//
//                is SuffixExpressionContext -> {
//                    isArrayCounter++
//                    rewriteTypeRecursive(toRewrite.expr())
//                }
//
//                is PowerExpressionContext -> {
//                    isArrayCounter++
//                    rewriteTypeRecursive(toRewrite.operand)
//                }
//
//                is TupleExpressionContext -> {
//                    if (toRewrite.exprs() == null) return "Unit"
//
//                    val clsParams = toRewrite.exprs().expr().joinToString { rewriteTypeRecursive(it) }
//                    "Tuple<$clsParams>"
//                }
//
//                is BinaryComputesExpressionContext -> {
//                    "(" + rewriteTypeRecursive(toRewrite.lhs) + ") -> " + rewriteTypeRecursive(
//                        toRewrite.rhs
//                    )
//                }
//
//                is OrExpressionContext -> {
//                    if (toRewrite.rhs.text == toRewrite.rhs.text) {
//                        rewriteTypeRecursive(toRewrite.lhs)
//                    } else {
//                        throw DetailedException("Unexpected return type: ${toRewrite.text}")
//                    }
//                }
//
//                is VariableContext -> if (toRewrite.text != "_") {
//                    toRewrite.varname.text + "p".repeat(toRewrite.squote().size)
//                } else "*"
//
//                is NestedExpressionContext -> rewriteTypeRecursive(toRewrite.expr())
//                is UnaryComputesExpressionContext -> rewriteTypeRecursive(toRewrite.expr())
//                is NumberContext -> toRewrite.text
        }
        return rewriteTypeRecursive(expr)
    }
}
