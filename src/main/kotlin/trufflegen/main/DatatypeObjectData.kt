package trufflegen.main

import trufflegen.antlr.CBSParser

class DatatypeObjectData(
    override val name: String,
    val definition: MutableList<String>,
    val datatypeComposites: MutableList<CBSParser.FunconExprContext>
) : ObjectDataContainer() {

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
