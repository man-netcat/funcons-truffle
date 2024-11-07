package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class Param(index: Int, valueExpr: ExprContext?, typeExpr: ExprContext) {
    val name: String = "p$index"
    val type: ParamType = ParamType(typeExpr)
    val value: String? = valueExpr?.text

    override fun toString(): String {
        return "$value: ${type.text}"
    }
}
