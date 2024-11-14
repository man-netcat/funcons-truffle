package trufflegen.main

import trufflegen.antlr.CBSParser
import trufflegen.antlr.CBSParser.AliasDefinitionContext

class FunconObjectWithoutRules(
    name: String,
    ctx: CBSParser.FunconDefinitionContext,
    params: List<Param>,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    builtin: Boolean,
    metavariables: Map<String, String>
) :
    FunconObject(name, ctx, params, returns, aliases, builtin, metavariables) {
    override fun makeContent(): String {
        val content = "return ${buildTypeRewrite(returns)}()"
        return makeExecuteFunction(content, returnStr)
    }
}