package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconObjectWithRewrite(
    name: String,
    val ctx: FunconDefinitionContext,
    params: List<Param>,
    val rewritesTo: ExprContext,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    builtin: Boolean,
) : FunconObject(name, params, returns, aliases, builtin) {
    override fun makeContent(): String {
        val content = "return " + buildRewrite(ctx, rewritesTo)
        val returnStr = buildTypeRewrite(returns)
        return makeExecuteFunction(content, returnStr)
    }
}