package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    internal open val context: FunconDefinitionContext,
    override val name: String,
    open val params: List<Param>,
    private val returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
) : Object(aliases) {

    private val paramsAfterVarargs: Int
        get() {
            val varargParamIndex = params.indexOfFirst { it.type.isVararg }
            return params.size - varargParamIndex
        }

    private val numVarargs: Int
        get() {
            return params.count { param -> param.type.isVararg }
        }

    private fun extractArgs(funcon: ParseTree): List<ExprContext> {
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

    internal fun buildRewrite(
        ruleDef: ParseTree, rewritesTo: ParseTree, params: List<Param>,
    ): String {
        val args = extractArgs(ruleDef)
        val rewriteVisitor = RewriteVisitor(rewritesTo, params, args)
        println("Before: ${rewritesTo.text}")
        val rewritten = rewriteVisitor.visit(rewritesTo)
        println("After: $rewritten")
        return rewritten
    }

    abstract fun makeContent(): String

    override fun generateCode(): String {
        val paramsStr = params.map { param ->
            val annotation = if (param.type.isVararg) "@Children private vararg val" else "@Child private val"
            Triple(annotation, param.name, "FCTNode")
        }

        val content = makeContent()

        val cls = makeClass(nodeName, emptyList(), paramsStr, emptyList(), content)

        return cls
    }
}

