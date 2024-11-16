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
        val content = if (!builtin) makeContent() else todoExecute(returnStr)

        return makeClass(
            nodeName,
            content = content,
            constructorArgs = paramsStr,
            typeParams = typeParams,
            superClass = emptySuperClass(COMPUTATION),
            annotations = listOf("Funcon")
        )
    }
}

