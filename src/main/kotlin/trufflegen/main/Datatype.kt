package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext
import trufflegen.antlr.CBSParser.FunconExpressionContext

class Datatype(
    override val name: String,
    val definition: MutableList<String>,
    val datatypeComposites: MutableList<FunconExpressionContext>,
    aliases: List<AliasDefinitionContext>,
) : Object(aliases) {

    override fun generateCode(): String {
        val cls = makeClass(
            name = name,
            annotations = emptyList(),
            constructorArgs = emptyList(),
            properties = emptyList(),
            content = ""
        )
        return cls
    }
}
