package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class Type(val expr: ExprContext?, val isParam: Boolean = false) {
    var computes = false
    var complement = false
    val text = expr?.text ?: "null"
    var starExpr = false
    var plusExpr = false
    var qmarkExpr = false
    var isVararg = false

    init {
        if (expr != null) {
            println("type: ${expr::class.simpleName}, ${expr.text}")
            when (expr) {
                is SuffixExpressionContext -> {
                    when (expr.op.text) {
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
//                    when (val operand = expr.operand) {
//                        is NestedExpressionContext -> {
//                            when (val nestedExpr = operand.expr()) {
//
//                            }
//                        }
//                    }
                }

                is UnaryComputesExpressionContext -> computes = true
            }
        }
    }

    val annotation: String
        get() = if (isVararg) "@Children private vararg val" else "@Child private val"
}
