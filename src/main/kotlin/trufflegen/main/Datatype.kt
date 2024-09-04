package trufflegen.main

import trufflegen.antlr.CBSParser.FunconExpressionContext

class Datatype(
    override val name: String,
    val definition: MutableList<String>,
    val datatypeComposites: MutableList<FunconExpressionContext>
) : Object() {

    override fun generateCode(objects: Map<String, Object>): String {
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
