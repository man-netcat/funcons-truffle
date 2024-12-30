package main.dataclasses

import cbs.CBSParser.*

class Type(val expr: ExprContext?, val isParam: Boolean = false, val isOverride: Boolean = false) {
    val text = expr?.text ?: "null"
    var computes = false
    var isComplement = false
    var isStarExpr = false
    var isPlusExpr = false
    var isQmarkExpr = false
    var isVararg = false
    var isArray = false

    fun handleUnaryComputesExpression(expr: UnaryComputesExpressionContext) {
        computes = true
        val unaryComputesExpression = expr.operand
        if (unaryComputesExpression is SuffixExpressionContext) isArray = true
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

                        "?" -> isQmarkExpr = true
                    }
                    val operand = expr.operand
                    if (operand is NestedExpressionContext) {
                        val nestedExpr = operand.expr()
                        if (nestedExpr is UnaryComputesExpressionContext) handleUnaryComputesExpression(nestedExpr)
                    }
                }

                is UnaryComputesExpressionContext -> handleUnaryComputesExpression(expr)
                is ComplementExpressionContext -> isComplement = true
            }

            if (isVararg && !isParam) throw IllegalStateException("A non-parameter type cannot be vararg")
        }
    }
}
