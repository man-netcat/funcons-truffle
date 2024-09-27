package trufflegen.main

import trufflegen.antlr.CBSParser.*

open class TypeObject(
    internal open val context: TypeDefinitionContext,
    override val name: String,
    private val params: List<Param>,
    private val definition: ExprContext?,
    aliases: MutableList<AliasDefinitionContext>,
) : Object(aliases) {
    override fun generateCode(): String {
        if (definition == null) {
            return ""
        }

        val args = params.map { param -> param.valueExpr ?: param.typeExpr }
        val rewriteVisitor = RewriteVisitor(definition, params, args)
        val rewritten = rewriteVisitor.visit(definition)
        return rewritten
    }

    override fun generateBuiltinTemplate(): String {
        if (definition == null) {
            return ""
        }

        val args = params.map { param -> param.valueExpr ?: param.typeExpr }
        val rewriteVisitor = RewriteVisitor(definition, params, args)
        val rewritten = rewriteVisitor.visit(definition)
        return rewritten
    }
}
