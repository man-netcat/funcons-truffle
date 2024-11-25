package trufflegen.main

import trufflegen.antlr.CBSParser.*

class AlgebraicDatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    aliases: MutableList<AliasDefinitionContext>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        return makeClass(
            nodeName,
            body = false,
            constructorArgs = valueParams,
            keywords = listOf("open"),
            typeParams = emptyList(), // TODO Fix
            superClass = emptySuperClass(TERMINAL),
            annotations = listOf("Datatype")
        )
    }
}

