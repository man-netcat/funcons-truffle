package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSParser.*

abstract class Object(
    val name: String,
    val ctx: ParseTree,
    private val params: List<Param>,
    private val aliases: List<AliasDefinitionContext>,
    private val fileMetavariables: Map<ExprContext, ExprContext>,
) {
    abstract fun generateCode(): String

    fun aliasStr(): String {
        return aliases.joinToString("\n") { alias -> makeTypeAlias(toClassName(alias.name.text), nodeName) }
    }

    internal val nodeName: String
        get() {
            return toClassName(name)
        }

    fun extractArgs(funcon: ParseTree): List<ExprContext> {
        return when (funcon) {
            is FunconExpressionContext -> {
                when (val args = funcon.args()) {
                    is NoArgsContext -> emptyList()
                    is SingleArgsContext -> listOf(args.expr())
                    is MultipleArgsContext -> args.exprs().expr()
                    is ListIndexExpressionContext -> args.indices.expr()
                    else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
                }
            }

            is FunconDefinitionContext -> funcon.params()?.param()?.map { it.value ?: it.type } ?: emptyList()

            else -> throw DetailedException("Unexpected funcon type: ${funcon::class.simpleName}, ${funcon.text}")
        }
    }

    fun buildRewrite(
        definition: ParseTree,
        toRewrite: ParseTree,
        entities: Map<String, String> = emptyMap()
    ): String {
        val args = extractArgs(definition)
        val rewriteVisitor = RewriteVisitor(toRewrite, params, args, entities)
        val rewritten = rewriteVisitor.visit(toRewrite)
        return rewritten
    }

    fun buildTypeRewrite(type: Type): String {
        val rewriteVisitor = TypeRewriteVisitor(type)
        val rewritten = rewriteVisitor.visit(type.expr)
        return rewritten
    }

    fun buildTypeRewriteWithComplement(type: Type): Pair<String, Boolean> {
        val rewriteVisitor = TypeRewriteVisitor(type)
        val rewritten = rewriteVisitor.visit(type.expr)
        val complement = rewriteVisitor.complement
        return rewritten to complement
    }

    fun makeTypeParams(): Set<String> {
        // TODO fix question marks
        return fileMetavariables.keys
            .filter { ArgVisitor(it.text).visit(ctx) }
            .map { buildTypeRewrite(ReturnType(it)) }
            .toSet()
    }
}

