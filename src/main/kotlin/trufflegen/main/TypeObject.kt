package trufflegen.main

import trufflegen.antlr.CBSParser.*

class TypeObject(
    name: String,
    private val params: List<Param>,
    private val operator: String,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    val builtin: Boolean,
) : Object(name, emptyList(), aliases) {
    fun makeParams(definition: FunconExpressionContext): List<String> {
        return extractArgs(definition).map { param -> buildRewrite(definition, param) }
    }

    private fun makeRewrite(): String {
        val paramsStr = params.map { param ->
            val annotation = param.type.annotation
            val paramTypeStr = buildTypeRewrite(param.type)
            makeParam(annotation, param.name, paramTypeStr)
        }
        val superClasses = if (definitions.isNotEmpty()) {
            definitions.map { definition ->
                when (definition) {
                    is FunconExpressionContext -> toClassName(definition.name.text) to makeParams(definition)
                    else -> throw DetailedException("Unexpected Expr type: ${definition.text}")
                }
            }
        } else emptyList()
        return makeClass(
            nodeName,
            keywords = listOf("sealed"),
            constructorArgs = paramsStr,
            superClasses = superClasses,
            body = false
        )
    }

    private fun makeSubtype(): String {
        return "TODO(\"some type\")"
    }

    override fun generateCode(): String {
        return if (definitions.isNotEmpty()) {
            when (operator) {
                "<:" -> makeSubtype()
                "~>" -> makeRewrite()
                else -> throw DetailedException("Unexpected operator: $operator")
            }
        } else {
            makeClass(
                nodeName,
                keywords = listOf("sealed"),
                body = false
            )
        }
    }
}

