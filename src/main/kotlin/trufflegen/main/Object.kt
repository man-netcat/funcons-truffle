package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSParser.*

abstract class Object(
    val name: String,
    val ctx: ParseTree,
    private val params: List<Param>,
    private val aliases: List<AliasDefinitionContext>,
    private val metavariables: Map<String, String>,
) {
    abstract fun generateCode(): String

    fun aliasStr(): String {
        return aliases.joinToString("\n") { alias -> makeTypeAlias(toClassName(alias.name.text), nodeName) }
    }

    internal val nodeName: String
        get() = toClassName(name)

    fun extractArgs(obj: ParseTree): List<ExprContext> {
        fun extractParams(params: ParamsContext?): List<ExprContext> {
            return params?.param()?.map { it.value ?: it.type } ?: emptyList()
        }
        return when (obj) {
            is FunconExpressionContext -> {
                when (val args = obj.args()) {
                    is NoArgsContext -> emptyList()
                    is SingleArgsContext -> listOf(args.expr())
                    is MultipleArgsContext -> args.exprs().expr()
                    is ListIndexExpressionContext -> args.indices.expr()
                    else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
                }
            }

            is FunconDefinitionContext -> extractParams(obj.params())
            is TypeDefinitionContext -> extractParams(obj.params())
            is DatatypeDefinitionContext -> extractParams(obj.params())

            else -> throw DetailedException("Unexpected funcon type: ${obj::class.simpleName}, ${obj.text}")
        }
    }

    fun buildRewrite(
        definition: ParseTree,
        toRewrite: ParseTree,
        entities: Map<String, String> = emptyMap(),
        makeParamStr: Boolean = false,
        forcedArgIndex: Int = -1
    ): String {
        val args = extractArgs(definition)
        val rewriteVisitor = RewriteVisitor(toRewrite, params, args, entities)
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

    fun buildTypeRewriteWithComplement(type: Type, nullable: Boolean = true): Pair<String, Boolean> {
        val rewriteVisitor = TypeRewriteVisitor(type, nullable)
        val rewritten = rewriteVisitor.visit(type.expr)
        val complement = rewriteVisitor.complement
        return rewritten to complement
    }

    fun buildParamStrs() = params.partition { it.value != null }.let { (valueParams, typeParams) ->
        Pair(valueParams.map { makeParam(it.type.annotation, it.name, buildTypeRewrite(it.type)) },
            typeParams.map {
                val metavar = buildTypeRewrite(it.type)
                metavar to metavariables[metavar]
            })
    }

    fun buildArgStrs(args: List<ExprContext>): Pair<MutableList<String>, MutableList<String>> {
        val valueParamStrings = mutableListOf<String>()
        val typeParamStrings = mutableListOf<String>()

        args.forEach { arg ->
            when (arg) {
                is TypeExpressionContext -> valueParamStrings.add(arg.value.text)
                is FunconExpressionContext -> {
                    if (arg.name.text in globalObjects.keys) {
                        typeParamStrings.add(buildTypeRewrite(ReturnType(arg), nullable = false))
                    } else throw DetailedException("Unexpected funcon: ${arg.name.text}")
                }

                is VariableContext -> {
                    if (arg.text in metavariables) {
                        typeParamStrings.add(arg.text)
                    } else {
                        valueParamStrings.add(buildRewrite(ctx, arg))
                    }
                }

                is SuffixExpressionContext -> "null".also { println(it) }

                is NumberContext -> valueParamStrings.add(buildRewrite(ctx, arg))

                else -> throw DetailedException("Unexpected expression: ${arg::class.simpleName}, ${arg.text}")
            }
        }

        return valueParamStrings to typeParamStrings
    }
}
