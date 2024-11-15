package trufflegen.main

import trufflegen.antlr.CBSParser.*

class DatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    operator: String,
    aliases: MutableList<AliasDefinitionContext>,
    val builtin: Boolean,
    metavariables: Map<String, String>,
) : Object(name, ctx, params, aliases, metavariables) {
    override fun generateCode(): String {
        val superClass = makeClass(
            nodeName,
            body = false,
            constructorArgs = paramsStr,
            keywords = listOf("open"),
            typeParams = typeParams,
            superClass = emptySuperClass(TERMINAL),
        )

        return superClass
    }
}

