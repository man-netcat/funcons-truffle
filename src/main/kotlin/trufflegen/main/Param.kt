package trufflegen.main

import trufflegen.antlr.CBSParser.*

class Param(private val index: Int, internal val valueExpr: ExprContext?, private val typeExpr: ExprContext) {
    val name: String = "p$index"
    val type: ParamType = ParamType(typeExpr)
    val value: String = valueExpr?.text ?: "None"
}
