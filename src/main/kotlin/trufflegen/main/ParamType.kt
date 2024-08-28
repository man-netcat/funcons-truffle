package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class ParamType(type: ExprContext) : Type(type) {
    override val isVararg: Boolean
        get() = typeData.stars >= 1 || typeData.pluses >= 1

    override val isArray: Boolean
        get() = typeData.stars == 2 || typeData.pluses == 2
}