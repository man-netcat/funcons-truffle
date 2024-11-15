package trufflegen.main

import trufflegen.antlr.CBSParser

class DatatypeFunconObject(
    name: String,
    ctx: CBSParser.FunconExpressionContext,
    params: List<Param>,
    private val superclass: DatatypeObject,
    aliases: List<CBSParser.AliasDefinitionContext>,
    metavariables: Map<String, String>
) : Object(name, ctx, params, aliases, metavariables) {
    override fun generateCode(): String {
        return makeClass(
            nodeName,
            constructorArgs = paramsStr,
            typeParams = typeParams,
            superClass = emptySuperClass(superclass.nodeName), // TODO Fix
            body = false
        )
    }
}