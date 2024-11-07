package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext

class FunconObjectWithoutRules(
    name: String,
    params: List<Param>,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    builtin: Boolean
) :
    FunconObject(name, params, returns, aliases, builtin) {
    override fun makeContent(): String {
        val content = "return ${buildTypeRewrite(returns)}()"
        return makeExecuteFunction(content, returnStr)
    }
}