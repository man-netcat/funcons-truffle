package main.objects

import antlr.CBSParser.*
import main.*

class FunconObjectWithoutRules(
    name: String,
    ctx: FunconDefinitionContext,
    params: List<Param>,
    returns: Type,
    aliases: List<String>,
    builtin: Boolean,
    metaVariables: MutableSet<Pair<String, String>>
) :
    FunconObject(name, ctx, params, returns, aliases, builtin, metaVariables) {
    override fun makeContent(): String {
        // TODO Fix
        val content = "return ${buildTypeRewrite(returns)}()"
        return makeExecuteFunction(content, returnStr)
    }
}