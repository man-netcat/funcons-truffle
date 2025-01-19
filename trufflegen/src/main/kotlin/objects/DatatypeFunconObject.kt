package main.objects

import cbs.CBSParser.FunconExpressionContext
import main.dataclasses.Param
import main.makeClass
import main.makeFunCall

class DatatypeFunconObject(
    name: String,
    ctx: FunconExpressionContext,
    params: List<Param>,
    private val superclass: AlgebraicDatatypeObject,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, emptyList(), metaVariables) {
    override fun generateCode(): String {
        return makeClass(
            nodeName,
            constructorArgs = valueParams,
            superClass = makeFunCall(
                superclass.nodeName,
                superclass.valueParams
            ),
            body = false,
            annotations = listOf("Funcon")
        )
    }
}