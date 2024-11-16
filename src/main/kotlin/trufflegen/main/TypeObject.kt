package trufflegen.main

import trufflegen.antlr.CBSParser.*

class TypeObject(
    name: String,
    ctx: TypeDefinitionContext,
    params: List<Param>,
    private val operator: String,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    val builtin: Boolean,
    metavariables: Map<String, String>,
) : Object(name, ctx, params, aliases, metavariables) {
    override fun generateCode(): String {
        val superClass: Triple<String, MutableList<String>, MutableList<String>>? =
            when (definitions.size) {
                1 -> {
                    val definition = definitions[0]
                    val args = extractArgs(definition)
                    val (argsStrs, superClassTypeParams) = buildArgStrs(args)

                    when (definition) {
                        is FunconExpressionContext -> Triple(
                            toClassName(definition.name.text), superClassTypeParams, argsStrs
                        )

                        else -> throw DetailedException("Unexpected Expr type: ${definition.text}")
                    }
                }

                2 -> null // TODO: make sure to check this someday

                else -> null
            }

        return makeClass(
            name = nodeName,
            keywords = listOf("open"),
            constructorArgs = paramsStr,
            superClass = superClass,
            typeParams = typeParams,
            body = false,
            annotations = listOf("Type")
        )
    }
}
