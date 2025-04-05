package main.dataclasses

import cbs.CBSParser.*

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
                is BinaryComputesExpressionContext -> computes = true
            }

            if (isSequence && !isParam) throw IllegalStateException("A non-parameter type cannot be vararg")
        }
    }

    override fun toString(): String = expr?.text ?: "null"
}
