package trufflegen.main

import trufflegen.antlr.CBSParser.*

class AlgebraicDatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: Map<String, String>,
) : Object(name, ctx, params, aliases, metavariables) {
    override fun generateCode(): String {
        return makeClass(
            nodeName,
            body = false,
            constructorArgs = valueParams,
            keywords = listOf("open"),
            typeParams = typeParams,
            superClass = emptySuperClass(TERMINAL),
            annotations = listOf("Datatype")
        )
    }
}

