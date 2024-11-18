package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSParser.*

abstract class Object(
    val name: String,
    val ctx: ParseTree,
    val params: List<Param>,
    private val aliases: List<AliasDefinitionContext>,
    private val metavariables: Map<String, String>,
) {
    abstract fun generateCode(): String

    val valueParams = params.filter { it.value != null }.map { param ->
        val annotation = param.type.annotation
        val paramTypeStr = buildTypeRewrite(param.type)
        makeParam(annotation, param.name, paramTypeStr)
    }

    val typeParams = params.filter { it.value == null }.map { param ->
        val metavar = buildTypeRewrite(param.type)
        metavar to metavariables[metavar]
    }

    fun aliasStr(): String {
        return aliases.joinToString("\n") { alias -> makeTypeAlias(toClassName(alias.name.text), nodeName) }
    }

    internal val nodeName: String
        get() = toClassName(name)

    fun buildRewrite(
        definition: ParseTree,
        toRewrite: ParseTree,
        entities: Map<String, String> = emptyMap(),
        makeParamStr: Boolean = false,
        forcedArgIndex: Int = -1
    ): String {
        val rewriteVisitor = RewriteVisitor(definition, toRewrite, this, entities)
        val rewritten = if (makeParamStr) {
            rewriteVisitor.makeParamStr(
                toRewrite.text,
                argIsArray = toRewrite is SuffixExpressionContext,
                stepSuffix = if (toRewrite is VariableStepContext) "p".repeat(toRewrite.squote().size) else "",
                forcedArgIndex = forcedArgIndex
            )
        } else rewriteVisitor.visit(toRewrite)
        return rewritten
    }
}
