package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconObjectWithRewrite(
    override val context: FunconDefinitionContext,
    override val name: String,
    override val params: List<Param>,
    private val rewritesTo: ExprContext,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
) : FunconObject(
    context, name, params, returns, aliases
) {
    override fun makeContent(): String {
        val content = "return ${buildRewrite(context, rewritesTo, params)}"
        return makeExecuteFunction(content)
    }
}