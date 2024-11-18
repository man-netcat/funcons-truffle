package trufflegen.main

import trufflegen.antlr.CBSParser

class DatatypeFunconObject(
    name: String,
    ctx: CBSParser.FunconExpressionContext,
    params: List<Param>,
    private val superclass: DatatypeObject,
    metavariables: Map<String, String>
) : Object(name, ctx, params, emptyList(), metavariables) {
    override fun generateCode(): String {
        return makeClass(
            nodeName,
            constructorArgs = valueParams,
            typeParams = typeParams,
            superClass = superclass.nodeName, // TODO Fix
            body = false,
            annotations = listOf("Funcon")
        )
    }
}