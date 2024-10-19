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
        println("type: $name")
        val content = if (definition != null) {
            val type = ReturnType(definition)
            val rewriteVisitor = TypeRewriteVisitor(type)
            rewriteVisitor.visit(definition)
        } else todoExecute()

        return makeTypeAlias(nodeName, content)
    }
}
