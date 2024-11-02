package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class Metavariable(name: String, private val definition: ExprContext) : Object(name, emptyList(), emptyList()) {
    override fun generateCode(): String {
        val def = buildTypeRewrite(ReturnType(definition), nullable = false)
        return makeClass(
            name,
            keywords = listOf("private"),
            superClasses = listOf(def to emptyList()),
            body = false
        )
    }
}