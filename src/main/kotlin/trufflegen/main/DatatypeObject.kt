package trufflegen.main

import trufflegen.antlr.CBSParser.*

class DatatypeObject(
    override val name: String,
    private val params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
) : Object(aliases, metavariables) {
    override fun generateCode(): String {
//        definitions.map { def -> println("${def::class.simpleName}: ${def.text}") }
        return ""
    }

    override fun generateBuiltinTemplate(): String {
        return ""
    }
}
