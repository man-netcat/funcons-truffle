package trufflegen.main

import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    name: String,
    val params: List<Param>,
    val returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    val builtin: Boolean,
) : Object(name, params, aliases) {
    abstract fun makeContent(): String

    override fun generateCode(): String {
        val paramsStr = params.map { param ->
            val annotation = param.type.annotation
            val paramTypeStr = buildTypeRewrite(param.type)
            makeParam(annotation, param.name, paramTypeStr)
        }

        val content = if (!builtin) makeContent() else todoExecute()

        return makeClass(
            nodeName,
            content = content,
            constructorArgs = paramsStr,
            superClasses = listOf(COMPUTATION to emptyList()),
        )
    }
}

