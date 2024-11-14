package trufflegen.main

import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    name: String,
    ctx: FunconDefinitionContext,
    val params: List<Param>,
    val returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    val builtin: Boolean,
    metavariables: Map<String, String>,
) : Object(name, ctx, params, aliases, metavariables) {
    abstract fun makeContent(): String

    val returnStr = buildTypeRewrite(returns)

    override fun generateCode(): String {
        val paramsStr = params.map { param ->
            val annotation = param.type.annotation
            val paramTypeStr = buildTypeRewrite(param.type)
            makeParam(annotation, param.name, paramTypeStr)
        }

        val content = if (!builtin) makeContent() else todoExecute(returnStr)

        return makeClass(
            nodeName,
            content = content,
            constructorArgs = paramsStr,
            superClass = emptySuperClass(COMPUTATION),
        )
    }
}

