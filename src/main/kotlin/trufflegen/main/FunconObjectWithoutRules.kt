package trufflegen.main

import trufflegen.antlr.CBSParser

class FunconObjectWithoutRules(
    name: String,
    ctx: CBSParser.FunconDefinitionContext,
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