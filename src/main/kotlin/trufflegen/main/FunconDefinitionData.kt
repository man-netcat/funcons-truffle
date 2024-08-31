package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSParser.*

abstract class FunconDefinitionData(
    internal open val context: FunconDefinitionContext,
    override val name: String,
    open val params: List<Param>,
    private val returns: ReturnType,
    private val aliases: List<AliasDefinitionContext>,
) : DefinitionDataContainer() {

    private fun extractArgs(funcon: ParseTree): List<ExprContext> {
        return when (funcon) {
            is FunconExpressionContext -> {
                when (val args = funcon.args()) {
                    is NoArgsContext -> emptyList()
                    is SingleArgsContext -> listOf(args.expr())
                    is MultipleArgsContext -> args.exprs().expr()
                    is ListIndexExpressionContext -> args.indices.expr()
                    else -> throw Exception("Unexpected args type: ${args::class.simpleName}")
                }
            }

            is FunconDefinitionContext -> funcon.params()?.param()?.map { it.value ?: it.type } ?: emptyList()

            else -> throw Exception("Unexpected funcon type: ${funcon::class.simpleName}")
        }
    }

    internal fun buildRewrite(
        ruleDef: ParseTree, rewriteExpr: ParseTree, params: List<Param>
    ): String {
        if (ruleDef !is FunconExpressionContext && ruleDef !is FunconDefinitionContext) {
            throw Exception("Unexpected rule definition ${ruleDef::class.simpleName}")
        }
        val args = extractArgs(ruleDef)
        val rewriteVisitor = RewriteVisitor(params, args)
        println("Before: ${rewriteExpr.text}")
        val rewritten = rewriteVisitor.visit(rewriteExpr)
        println("After: $rewritten")
        return rewritten
    }

    abstract fun makeContent(): String

    override fun generateCode(): String {

        try {
            val imports = makeImports(
                listOf(
                    "fctruffle.main.*",
                    "com.oracle.truffle.api.frame.VirtualFrame",
                    "com.oracle.truffle.api.nodes.NodeInfo",
                    "com.oracle.truffle.api.nodes.Node.Child"
                )
            )

            val paramsStr = params.map { param ->
                val annotation = if (param.type.isVararg) "@Children private vararg val" else "@Child private val"
                Triple(annotation, param.string, "FCTNode")
            }

            val content = makeContent()

            val cls = makeClass(nodeName, emptyList(), paramsStr, emptyList(), content)

            val aliasStrs =
                aliases.joinToString("\n") { alias -> makeTypeAlias(toClassName(alias.name.text), nodeName) }

            val file = makeFile("fctruffle.generated", imports, cls, aliasStrs)
            return file
        } catch (e: Exception) {
            println("Failed to build Funcon: ${name}. Error: ${e.message}")
            return ""
        }
    }
}

