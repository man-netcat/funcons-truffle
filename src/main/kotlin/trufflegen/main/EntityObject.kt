package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext
import trufflegen.antlr.CBSParser.ExprContext

class EntityObject(
    name: String,
    params: List<Param>,
    aliases: List<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
) : Object(name, params, aliases, metavariables) {
    override fun generateCode(): String {
        TODO("Not yet implemented")
    }
}