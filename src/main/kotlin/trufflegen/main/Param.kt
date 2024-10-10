package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class Param(index: Int, valueExpr: ExprContext?, val typeExpr: ExprContext) {
    val name: String = "p$index"
    val type: ParamType = ParamType(typeExpr)
    val value: String? = valueExpr?.text
}
