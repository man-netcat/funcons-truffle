package trufflegen.main

import trufflegen.antlr.CBSParser

class DatatypeFunconObject(
    name: String,
    ctx: CBSParser.FunconExpressionContext,
    params: List<Param>,
    private val superclass: AlgebraicDatatypeObject,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, emptyList(), metaVariables) {
    override fun generateCode(): String {
        return makeClass(
            nodeName,
            constructorArgs = valueParams,
            typeParams = emptyList(), // TODO Fix
            superClass = makeFun(
                superclass.nodeName,
                emptyList(), // TODO Fix
                superclass.valueParams
            ),
            body = false,
            annotations = listOf("Funcon")
        )
    }
}