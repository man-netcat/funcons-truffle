package main

import antlr.CBSParser

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
            typeParams = metaVariables.toList(),
            superClass = makeFun(
                superclass.nodeName,
                superclass.metaVariables,
                superclass.valueParams
            ),
            body = false,
            annotations = listOf("Funcon")
        )
    }
}