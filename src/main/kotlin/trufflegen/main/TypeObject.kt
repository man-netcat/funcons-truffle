package trufflegen.main

import trufflegen.antlr.CBSParser.*

open class TypeObject(
    internal open val context: TypeDefinitionContext,
    override val name: String,
    private val definition: ExprContext?,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    val builtin: Boolean,
) : Object(aliases, metavariables, builtin) {
    override fun generateCode(): String {
        println(context.text)
        if (definition == null) {
            return ""
        }

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
