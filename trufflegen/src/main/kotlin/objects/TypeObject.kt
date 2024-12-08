package main.objects

import cbs.CBSParser
import main.exceptions.*
import main.*
import main.dataclasses.Param

class TypeObject(
    name: String,
    ctx: CBSParser.TypeDefinitionContext,
    params: List<Param>,
    private val definitions: List<CBSParser.ExprContext>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        val superClass: String = when (definitions.size) {
            0 -> ""
            1 -> {
                val definition = definitions[0]
                if (definition is CBSParser.FunconExpressionContext) {
                    buildRewrite(ctx, definition)
                } else throw DetailedException("Unexpected definition ${definition.text}")
            }

            2 -> "" // TODO: Fix this edge case
            else -> throw DetailedException("Unexpected amount of definitions, ${definitions.joinToString()} has ${definitions.size} items")
        }

        return makeClass(
            name = nodeName,
            keywords = listOf("open"),
            constructorArgs = valueParams,
            superClass = superClass,
            typeParams = metaVariables.toList(),
            body = false,
            annotations = listOf("Type")
        )
    }
}