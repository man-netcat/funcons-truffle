package trufflegen.main

import trufflegen.antlr.CBSParser.*

class DatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    operator: String,
    aliases: MutableList<AliasDefinitionContext>,
    builtin: Boolean,
    metavariables: Map<String, String>,
) : Object(name, ctx, params, aliases, metavariables) {
    override fun generateCode(): String {
        // TODO: Fix builtin inheriting types

        val superClass = makeClass(
            nodeName,
            body = false,
            constructorArgs = valueParams,
            keywords = listOf("open"),
            typeParams = typeParams,
            superClass = TERMINAL,
            annotations = listOf("Datatype")
        )

        return superClass
    }
}

