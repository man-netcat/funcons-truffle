package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import main.dataclasses.Param
import main.emptySuperClass
import main.makeClass
import main.toClassName

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
            superClass = emptySuperClass(toClassName("values")),
            annotations = listOf("DataType")
        )
    }
}

