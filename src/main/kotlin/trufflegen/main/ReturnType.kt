package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

open class ReturnType(expr: ExprContext) : Type(expr) {
    override val isVararg: Boolean
        get() = false

    override val isArray: Boolean
        get() = stars == 1 || pluses == 1
}