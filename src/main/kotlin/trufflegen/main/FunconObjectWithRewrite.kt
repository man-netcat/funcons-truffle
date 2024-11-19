package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconObjectWithRewrite(
    name: String,
    ctx: FunconDefinitionContext,
    params: List<Param>,
    val rewritesTo: ExprContext,
    returns: Type,
    aliases: List<AliasDefinitionContext>,
    builtin: Boolean,
    metavariables: Map<String, String>,
) : FunconObject(name, ctx, params, returns, aliases, builtin, metavariables) {
    override fun makeContent(): String {
        val content = "return " + buildRewrite(this@FunconObjectWithRewrite.ctx, rewritesTo)
        return makeExecuteFunction(content, returnStr)
    }
}