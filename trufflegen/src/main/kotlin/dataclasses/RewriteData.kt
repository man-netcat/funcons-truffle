package main.dataclasses

import cbs.CBSParser.ExprContext

class RewriteData(
    val value: ExprContext?,
    val type: ExprContext?,
    val str: String,
) {
    operator fun component1(): ExprContext? = value
    operator fun component2(): ExprContext? = type
    operator fun component3(): String = str

    override fun toString(): String {
        return "(${value?.text}, ${type?.text}, $str)"
    }
}
