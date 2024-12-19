package objects

import cbs.CBSParser.FunconDefinitionContext
import main.*
import main.dataclasses.Param
import main.dataclasses.Type
import main.objects.Object

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

    val returnStr = if (!returns.computes) {
        buildTypeRewrite(returns, nullable = true)
    } else COMPUTATION

    override fun generateCode(): String {
        val skipCriteria = !listOf(
            builtin,               // Builtins should be implemented manually
            paramsAfterVararg > 0  // This behaviour is not yet implemented
        ).any { it }
        val content = if (skipCriteria) makeContent() else todoExecute(returnStr)

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

