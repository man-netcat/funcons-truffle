package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconObjectWithRewrite(
    name: String,
    val definition: FunconDefinitionContext,
    params: List<Param>,
    val rewritesTo: ExprContext,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    builtin: Boolean,
) : FunconObject(name, params, returns, aliases, metavariables, builtin) {
    override fun makeContent(): String {
        val content = "return " + buildRewrite(definition, rewritesTo)
        val returnStr = buildTypeRewrite(returns)
        return makeExecuteFunction(content, returnStr)
    }
}