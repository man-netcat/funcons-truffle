package main.objects

import antlr.CBSParser.*
import main.*

abstract class FunconObject(
    name: String,
    ctx: FunconDefinitionContext,
    params: List<Param>,
    val returns: Type,
    aliases: List<String>,
    val builtin: Boolean,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    abstract fun makeContent(): String

    val returnStr = buildTypeRewrite(returns, nullable = false)

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

