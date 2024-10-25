package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconObjectWithRewrite(
    name: String,
    ctx: FunconDefinitionContext,
    params: List<Param>,
    val rewritesTo: ExprContext,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    builtin: Boolean,
) : FunconObject(name, ctx, params, returns, aliases, metavariables, builtin) {
    override fun makeContent(): String {
        val content = "return " + buildRewrite(ctx, rewritesTo)
        val returnStr = buildTypeRewrite(returns)
        return makeExecuteFunction(content, returnStr)
    }
}