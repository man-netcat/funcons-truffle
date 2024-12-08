package main.objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Param

class AlgebraicDatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        return makeClass(
            nodeName,
            body = false,
            constructorArgs = valueParams,
            keywords = listOf("open"),
            typeParams = metaVariables.toList(),
            superClass = emptySuperClass(TERMINAL),
            annotations = listOf("Datatype")
        )
    }
}

