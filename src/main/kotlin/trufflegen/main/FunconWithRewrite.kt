package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconWithRewrite(
    override val context: FunconDefinitionContext,
    override val name: String,
    override val params: List<Param>,
    private val rewritesTo: ExprContext,
    private val returns: ReturnType,
    private val aliases: List<AliasDefinitionContext>
) : Funcon(
    context, name, params, returns, aliases
) {
    override fun makeContent(): String {
        val content = "return ${buildRewrite(context, rewritesTo, params)}"
        return makeExecuteFunction(content)
    }
}