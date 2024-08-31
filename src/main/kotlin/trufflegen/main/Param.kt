package trufflegen.main

import trufflegen.antlr.CBSParser.*

class Param(private val index: Int, internal val valueExpr: ExprContext?, private val typeExpr: ExprContext) {
    val string: String
        get() = "p$index"

    val type: ParamType
        get() = ParamType(typeExpr)

    val value: String
        get() = valueExpr?.text ?: "None"
}
