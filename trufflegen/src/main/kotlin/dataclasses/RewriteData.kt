package main.dataclasses

import cbs.CBSParser.ExprContext

class RewriteData(
    private val value: ExprContext?,
    private val type: ExprContext?,
    private val str: String,
    val sizeCondition: Pair<String, Int>? = null,
) {
    operator fun component1(): ExprContext? = value
    operator fun component2(): ExprContext? = type
    operator fun component3(): String = str

    override fun toString(): String {
        return "(${value?.text}, ${type?.text}, $str)"
    }
}
