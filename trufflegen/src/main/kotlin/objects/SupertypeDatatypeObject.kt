package main.objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Param
import main.exceptions.DetailedException

class SupertypeDatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        println("processing type $name")

        val superClass = if (definitions.size == 1) {
            buildRewrite(ctx, definitions[0])
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