package trufflegen.main

import trufflegen.antlr.CBSParser

class DatatypeObjectData(
    override val name: String,
    val definition: MutableList<String>,
    val datatypeComposites: MutableList<CBSParser.ExprContext>
) : ObjectDataContainer() {

    override fun generateCode(): String {
        val cls = makeClass(nodeName, listOf(), listOf(), listOf())
        return cls
    }
}
