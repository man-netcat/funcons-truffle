package trufflegen.main

import trufflegen.antlr.CBSParser.*

class TypeObject(
    name: String,
    private val params: List<Param>,
    ctx: TypeDefinitionContext,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    val builtin: Boolean,
) : Object(name, ctx, emptyList(), aliases, metavariables) {
    fun makeParams(definition: FunconExpressionContext): List<String> {
        return extractArgs(definition).map { param ->
            println("param: ${param.text}")
            buildRewrite(definition, param)
        }
    }

    override fun generateCode(): String {
        println("type: ${ctx.text}")
        val paramsStr = params.map { param ->
            val annotation = if (param.type.isVararg) "@Children private vararg val" else "@Child private val"
            val paramTypeStr = buildTypeRewrite(param.type)
            makeParam(annotation, param.name, paramTypeStr)
        }
        return if (definitions.isNotEmpty()) {
            val superClasses = definitions.map { definition ->
                when (definition) {
                    is FunconExpressionContext -> toClassName(definition.name.text) to makeParams(definition)
                    else -> throw DetailedException("Unexpected Expr type: ${definition.text}")
                }
            }
            makeClass(nodeName, constructorArgs = paramsStr, superClasses = superClasses, body = false)
        } else makeClass(nodeName, constructorArgs = paramsStr, body = false)
    }
}

