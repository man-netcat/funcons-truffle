package trufflegen.main

import trufflegen.antlr.CBSParser
import trufflegen.antlr.CBSParser.AliasDefinitionContext

class FunconObjectWithoutRules(
    name: String,
    ctx: CBSParser.FunconDefinitionContext,
    params: List<Param>,
    returns: Type,
    aliases: List<AliasDefinitionContext>,
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