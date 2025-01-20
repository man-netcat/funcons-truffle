package main.dataclasses

import cbs.CBSParser.*

class Type(val expr: ExprContext?, val isParam: Boolean = false) {
    var computes = false
    var isComplement = false
    var isStarExpr = false
    var isPlusExpr = false
    var isNullable = false
    var isVararg = false
    var isArray = false

    fun handleUnaryComputesExpression(expr: UnaryComputesExpressionContext) {
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

                is UnaryComputesExpressionContext -> handleUnaryComputesExpression(expr)
                is ComplementExpressionContext -> isComplement = true
                is OrExpressionContext -> isNullable = true
            }

            if (isVararg && !isParam) throw IllegalStateException("A non-parameter type cannot be vararg")
        }
    }

    override fun toString(): String = expr?.text ?: "null"
}
