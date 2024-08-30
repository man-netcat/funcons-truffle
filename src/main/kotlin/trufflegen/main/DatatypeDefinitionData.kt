package trufflegen.main

import trufflegen.antlr.CBSParser.FunconExpressionContext

class DatatypeDefinitionData(
    override val name: String,
    val definition: MutableList<String>,
    val datatypeComposites: MutableList<FunconExpressionContext>
) : DefinitionDataContainer() {

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
