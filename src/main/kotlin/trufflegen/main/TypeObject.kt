package trufflegen.main

import trufflegen.antlr.CBSParser.*

class TypeObject(
    name: String,
    ctx: TypeDefinitionContext,
    params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        println("processing type $name")

        val superClass: String = when (definitions.size) {
            0 -> ""
            1 -> {
                val definition = definitions[0]
                if (definition is FunconExpressionContext) {
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
