package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class ReturnType(type: ExprContext) : Type(type) {
    override val isVararg: Boolean
        get() = false

    override val isArray: Boolean
        get() = stars == 1 || pluses == 1
}