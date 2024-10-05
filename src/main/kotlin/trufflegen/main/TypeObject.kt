package trufflegen.main

import trufflegen.antlr.CBSParser.*

open class TypeObject(
    internal open val context: TypeDefinitionContext,
    override val name: String,
    private val definition: ExprContext?,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
) : Object(aliases, metavariables) {
    override fun generateCode(): String {
        println(context.text)
        if (definition == null) {
            return ""
        }

        val type = ReturnType(definition)
        val rewriteVisitor = TypeRewriteVisitor(type)
        val rewritten = rewriteVisitor.visit(definition)
        return makeTypeAlias(nodeName, rewritten)
    }

    override fun generateBuiltinTemplate(): String {
        val cls = makeTypeAlias(nodeName, "TODO(\"Implement me\")")

        return cls
    }
}
