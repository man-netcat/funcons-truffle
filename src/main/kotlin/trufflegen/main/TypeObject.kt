package trufflegen.main

import trufflegen.antlr.CBSParser.*

class TypeObject(
    name: String,
    params: List<Param>,
    private val operator: String,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    val builtin: Boolean,
) : Object(name, params, aliases) {
    fun makeParams(definition: FunconExpressionContext): List<String> {
        return extractArgs(definition).map { param -> buildRewrite(definition, param) }
    }

    override fun generateCode(): String {
        val (paramsStr, typeParams) = buildParamStrs()

        return makeClass(nodeName,
            keywords = listOf("sealed"),
            constructorArgs = paramsStr,
            superClasses = if (definitions.isNotEmpty()) definitions.map { definition ->
                when (definition) {
                    is FunconExpressionContext -> toClassName(definition.name.text) to makeParams(definition)
                    else -> throw DetailedException("Unexpected Expr type: ${definition.text}")
                }
            } else emptyList(),
            typeParams = typeParams.toSet(),
            body = false)
    }
}

