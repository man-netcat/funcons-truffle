package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class ParamType(expr: ExprContext) : Type(expr) {
    override val isVararg: Boolean
        get() = stars >= 1 || pluses >= 1

    override val isArray: Boolean
        get() = stars == 2 || pluses == 2
}