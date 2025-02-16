package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.dataclasses.Param
import main.exceptions.DetailedException
import main.rewrite

class SupertypeDatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    val definitions: List<ExprContext>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override val annotations: List<String>
        get() = listOf("DataType")
    override val superClassStr: String
        get() = if (definitions.size == 1) {
            rewrite(ctx, definitions[0])
        } else throw DetailedException("Has more than one superclass")
}