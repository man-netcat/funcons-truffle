package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.dataclasses.Param
import main.exceptions.DetailedException
import main.makeClass
import main.rewrite

class SupertypeDatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        val superClass = if (definitions.size == 1) {
            rewrite(ctx, definitions[0])
        } else throw DetailedException("Has more than one superclass")

        return makeClass(
            name = nodeName,
            keywords = listOf("open"),
            constructorArgs = valueParams,
            superClass = superClass,
            typeParams = emptyList(), // TODO Fix
            body = false,
            annotations = listOf("DataType")
        )
    }
}