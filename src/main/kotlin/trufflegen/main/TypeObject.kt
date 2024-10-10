package trufflegen.main

import trufflegen.antlr.CBSParser.*

open class TypeObject(
    name: String,
    ctx: TypeDefinitionContext,
    private val definition: ExprContext?,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    val builtin: Boolean,
) : Object(name, ctx, emptyList(), aliases, metavariables) {
    override fun generateCode(): String {
        if (definition == null) return ""

        val content = if (builtin) {
            "TODO(\"Implement me\")"
        } else {
            val type = ReturnType(definition)
            val rewriteVisitor = TypeRewriteVisitor(type)
            rewriteVisitor.visit(definition)
        }

        return makeTypeAlias(nodeName, content)
    }
}
