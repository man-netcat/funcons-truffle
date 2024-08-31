package trufflegen.main

import trufflegen.antlr.CBSParser

class ParamType(type: CBSParser.ExprContext) : Type(type) {
    override val isVararg: Boolean
        get() = stars >= 1 || pluses >= 1

    override val isArray: Boolean
        get() = stars == 2 || pluses == 2
}