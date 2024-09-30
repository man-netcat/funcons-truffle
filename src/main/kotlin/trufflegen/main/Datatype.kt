package trufflegen.main

import trufflegen.antlr.CBSParser.*

class Datatype(
    override val name: String,
    val definition: MutableList<String>,
    val datatypeComposites: MutableList<FunconExpressionContext>,
    aliases: List<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
) : Object(aliases, metavariables) {

    override fun generateCode(): String {
        val cls = makeClass(name, emptyList(), emptyList(), emptyList(), "")
        return cls
    }

    override fun generateBuiltinTemplate(): String {
        val cls = makeClass(name, emptyList(), emptyList(), emptyList(), "TODO(\"Implement me\")")
        return cls
    }
}
