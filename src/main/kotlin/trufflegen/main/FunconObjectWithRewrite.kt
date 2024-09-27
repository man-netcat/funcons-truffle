package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconObjectWithRewrite(
    override val context: FunconDefinitionContext,
    override val name: String,
    override val params: List<Param>,
    val rewritesTo: ExprContext,
    val returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
) : FunconObject(
    context, name, params, returns, aliases
) {
    override fun makeContent(): String {
        val content = buildRewrite(context, rewritesTo, params)
        val returnStr = buildTypeRewrite(returns)
        return makeExecuteFunction(content, returnStr)
    }
}