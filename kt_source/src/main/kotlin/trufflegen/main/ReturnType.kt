package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class ReturnType(type: ExprContext) : Type(type) {
    override val isVararg: Boolean
        get() = false

    override val isArray: Boolean
        get() = typeData.stars == 1 || typeData.pluses == 1
}