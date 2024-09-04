package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext
import trufflegen.antlr.CBSParser.TypeDefinitionContext

open class TypeObject(
    internal open val context: TypeDefinitionContext,
    override val name: String,
    private val params: List<Param>,
    private val definition: ExprContext?
) : Object() {
    override fun generateCode(objects: Map<String, Object>): String {
        if (definition == null) {
            return ""
        }

        println(context.text)

        val args = params.map { param -> param.typeExpr }
        val rewriteVisitor = RewriteVisitor(definition, params, args)
        println("Before: ${definition.text}")
        val rewritten = rewriteVisitor.visit(definition)
        println("After: $rewritten")
        return rewritten
    }
}
