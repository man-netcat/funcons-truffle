package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconObjectWithRewrite(
    override val context: FunconDefinitionContext,
    override val name: String,
    override val params: List<Param>,
    val rewritesTo: ExprContext,
    val returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
) : FunconObject(
    context, name, params, aliases, metavariables
) {
    override fun makeContent(): String {
        val content = "return " + buildRewrite(context, rewritesTo, params)
        val returnStr = buildTypeRewrite(returns)
        return makeExecuteFunction(content, returnStr)
    }
}