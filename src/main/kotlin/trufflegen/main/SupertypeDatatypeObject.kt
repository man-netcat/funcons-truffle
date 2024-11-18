package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext
import trufflegen.antlr.CBSParser.DatatypeDefinitionContext
import trufflegen.antlr.CBSParser.ExprContext

class SupertypeDatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: List<AliasDefinitionContext>,
    metavariables: Map<String, String>
) : Object(name, ctx, params, aliases, metavariables) {
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
            typeParams = typeParams,
            body = false,
            annotations = listOf("DataType")
        )
    }
}