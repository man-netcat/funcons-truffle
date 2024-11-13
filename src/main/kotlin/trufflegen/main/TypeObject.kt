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
    metavariables: Set<String>,
) : Object(name, ctx, params, aliases, metavariables) {
    override fun generateCode(): String {
        val (paramsStr, typeParams) = buildParamStrs()

        return makeClass(nodeName,
            keywords = listOf("open"),
            constructorArgs = paramsStr,
            superClasses = if (definitions.isNotEmpty()) definitions.map { definition ->
                val args = extractArgs(definition)
                val (argsStrs, superClassTypeParams) = buildArgStrs(args)
                when (definition) {
                    is FunconExpressionContext -> Triple(
                        toClassName(definition.name.text),
                        superClassTypeParams,
                        argsStrs
                    )

                    else -> throw DetailedException("Unexpected Expr type: ${definition.text}")
                }
            } else emptyList(),
            typeParams = typeParams.toSet(),
            body = false)
    }
}

