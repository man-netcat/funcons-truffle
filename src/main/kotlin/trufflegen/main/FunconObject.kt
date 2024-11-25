package trufflegen.main

import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    name: String,
    ctx: FunconDefinitionContext,
    params: List<Param>,
    val returns: Type,
    aliases: List<AliasDefinitionContext>,
    val builtin: Boolean,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    abstract fun makeContent(): String

    val returnStr = buildTypeRewrite(returns)

    override fun generateCode(): String {
        val content = if (!builtin) makeContent() else todoExecute(returnStr)

        return makeClass(
            nodeName,
            content = content,
            constructorArgs = valueParams,
            typeParams = metaVariables.toList(),
            superClass = emptySuperClass(COMPUTATION),
            annotations = listOf("Funcon")
        )
    }
}

