package main

import antlr.CBSParser.*

class Type(val expr: ExprContext?, val isParam: Boolean = false) {
    val text = expr?.text ?: "null"
    var computes = false
    var complement = false
    var starExpr = false
    var plusExpr = false
    var qmarkExpr = false
    var isVararg = false
    var isArray = false

    init {
        if (expr != null) {
            when (expr) {
                is SuffixExpressionContext -> {
                    val op = expr.op.text
                    when (op) {
                        "*" -> {
                            isVararg = true
                            starExpr = true
                        }

                        "+" -> {
                            isVararg = true
                            plusExpr = true
                        }

                        "?" -> qmarkExpr = true
                    }
                    val operand = expr.operand
                    if (operand is NestedExpressionContext) {
                        val nestedExpr = operand.expr()
                        if (nestedExpr is UnaryComputesExpressionContext) {
                            computes = true
                            val unaryComputesExpression = nestedExpr.operand
                            if (unaryComputesExpression is SuffixExpressionContext) isArray = true
                        }
                    }
                }

                is UnaryComputesExpressionContext -> computes = true
                is ComplementExpressionContext -> complement = true
            }
        }
    }

    val annotation: String
        get() = if (isVararg) "@Children vararg val" else "@Child val"
}
