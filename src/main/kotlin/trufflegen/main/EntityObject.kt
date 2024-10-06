package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext
import trufflegen.antlr.CBSParser.ExprContext

class EntityObject(
    override val name: String, aliases: List<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
) : Object(aliases, metavariables) {
    override fun generateCode(): String {
        TODO("Not yet implemented")
    }
}