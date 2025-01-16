package main.dataclasses

import cbs.CBSParser.ExprContext

class Param(index: Int, val valueExpr: ExprContext?, val typeExpr: ExprContext?) {
    val name = "p$index"
    val type = Type(typeExpr, isParam = true)
    val value = valueExpr?.text

    operator fun component1(): ExprContext? = valueExpr
    operator fun component2(): ExprContext? = typeExpr

    override fun toString(): String {
        return "$value: $type"
    }
}
