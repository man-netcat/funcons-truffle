package trufflegen.main

import trufflegen.antlr.CBSParser
import trufflegen.antlr.CBSParser.ExprContext

class DatatypeObject(
    override val name: String,
    private val params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: MutableList<CBSParser.AliasDefinitionContext>,
) : Object(aliases) {
    override fun generateCode(): String {
//        definitions.map { def -> println("${def::class.simpleName}: ${def.text}") }
        return ""
    }
}
