package trufflegen.main

import trufflegen.antlr.CBSParser
import trufflegen.antlr.CBSParser.ExprContext
import trufflegen.antlr.CBSParser.TypeDefinitionContext

open class TypeObject(
    internal open val context: TypeDefinitionContext,
    override val name: String,
    private val params: List<Param>,
    private val definition: ExprContext?,
    aliases: MutableList<CBSParser.AliasDefinitionContext>
) : Object(aliases) {
    override fun generateCode(): String {
        if (definition == null) {
            return ""
        }

        val aliasStrs = aliasStr()

        val args = params.map { param -> param.valueExpr ?: param.typeExpr }
        val rewriteVisitor = RewriteVisitor(definition, params, args)
        println("Before: ${definition.text}")
        val rewritten = rewriteVisitor.visit(definition)
        println("After: $rewritten")
        return rewritten
    }
}
