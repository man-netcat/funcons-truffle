package trufflegen.main

import trufflegen.antlr.CBSParser

class DatatypeFunconObject(
    name: String,
    ctx: CBSParser.FunconExpressionContext,
    params: List<Param>,
    private val superclass: AlgebraicDatatypeObject,
    metavariables: Map<String, String>
) : Object(name, ctx, params, emptyList(), metavariables) {
    override fun generateCode(): String {
        return makeClass(
            nodeName,
            constructorArgs = valueParams,
            typeParams = typeParams,
            superClass = makeFun(
                superclass.nodeName,
                superclass.typeParams.map { it.first },
                superclass.valueParams
            ),
            body = false,
            annotations = listOf("Funcon")
        )
    }
}