package main.objects

import cbs.CBSParser.*
import main.dataclasses.Param
import main.emptySuperClass
import main.exceptions.DetailedException
import main.makeClass
import main.rewrite
import main.toClassName

class TypeObject(
    name: String,
    ctx: TypeDefinitionContext,
    params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        val superClass: String = when (definitions.size) {
            0 -> emptySuperClass(toClassName("values"))
            1 -> {
                val definition = definitions[0]
                if (definition is FunconExpressionContext) {
                    rewrite(ctx, definition)
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
            body = false,
            annotations = listOf("Type")
        )
    }
}