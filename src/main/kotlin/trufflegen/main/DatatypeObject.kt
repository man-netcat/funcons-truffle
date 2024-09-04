package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

class DatatypeObject(
    override val name: String, private val params: List<Param>, private val definitions: List<ExprContext>
) : Object() {
    override fun generateCode(objects: Map<String, Object>): String {
        return ""
    }
}
