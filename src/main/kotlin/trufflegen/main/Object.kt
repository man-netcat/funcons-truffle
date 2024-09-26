package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSParser.AliasDefinitionContext
import trufflegen.antlr.CBSParser.ExprContext
import trufflegen.antlr.CBSParser.FunconDefinitionContext
import trufflegen.antlr.CBSParser.FunconExpressionContext
import trufflegen.antlr.CBSParser.ListIndexExpressionContext
import trufflegen.antlr.CBSParser.MultipleArgsContext
import trufflegen.antlr.CBSParser.NoArgsContext
import trufflegen.antlr.CBSParser.SingleArgsContext

abstract class Object(private val aliases: List<AliasDefinitionContext>) {
    abstract val name: String
    abstract fun generateCode(): String
    abstract fun generateBuiltinTemplate(): String

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
                    else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}")
                }
            }

            is FunconDefinitionContext -> funcon.params()?.param()?.map { it.value ?: it.type } ?: emptyList()

            else -> throw DetailedException("Unexpected funcon type: ${funcon::class.simpleName}")
        }
    }

    fun buildRewrite(
        ruleDef: ParseTree, rewritesTo: ParseTree, params: List<Param>,
    ): String {
        val args = extractArgs(ruleDef)
        val rewriteVisitor = RewriteVisitor(rewritesTo, params, args)
        val rewritten = rewriteVisitor.visit(rewritesTo)
        return rewritten
    }
}
