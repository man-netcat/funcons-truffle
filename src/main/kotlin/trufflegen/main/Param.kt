package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class Param(index: Int, val valueExpr: ExprContext?, val typeExpr: ExprContext?) {
    val name = "p$index"
    val type = Type(typeExpr, isParam = true)
    val value = valueExpr?.text

    override fun toString(): String {
        return "$value: ${type.text}"
    }
}
