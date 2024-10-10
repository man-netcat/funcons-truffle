package trufflegen.main

import trufflegen.antlr.CBSParser.*

class DatatypeObject(
    name: String,
    private val params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    val builtin: Boolean,
) : Object(name, params, aliases, metavariables) {

    fun argsToParams(args: List<ExprContext>): List<String> {
        return args.withIndex().map { (i, arg) ->
            when (arg) {
                is TypeExpressionContext -> {
                    val setType = ReturnType(arg.type)
                    val annotation = "@Child private val"
                    val paramTypeStr = buildTypeRewrite(setType)
                    makeParam(annotation, "p$i", paramTypeStr)
                }

                else -> throw DetailedException("Unexpected arg expression: ${arg::class.simpleName}")
            }
        }
    }

    override fun generateCode(): String {
        if (builtin) return makeClass(name, content = "TODO(\"Implement me\")")

        val typeParams = params.map { buildTypeRewrite(it.type) }.toSet()

        val superClass = makeClass(
            toClassName(name),
            typeParams = typeParams,
            superClass = "Computation",
            body = false,
            keywords = listOf("open"),
        )

        val clss = definitions.map { def ->
            when (def) {
                is FunconExpressionContext -> {
                    val className = toClassName(def.name.text)
                    val classParams = when (val args = def.args()) {
                        is MultipleArgsContext -> argsToParams(args.exprs().expr())
                        is SingleArgsContext -> argsToParams(listOf(args.expr()))
                        is NoArgsContext -> emptyList()

                        else -> throw DetailedException("Unexpected args expression: ${args::class.simpleName}")
                    }
                    makeClass(
                        className,
                        typeParams = typeParams,
                        constructorArgs = classParams,
                        superClass = toClassName(name),
                        body = false
                    )
                }

                is SetExpressionContext -> {
                    val typeStr = when (val setContent = def.expr()) {
                        is TypeExpressionContext -> {
                            val setType = ReturnType(setContent.type)
                            buildTypeRewrite(setType)
                        }

                        else -> throw DetailedException("Unexpected setContent definition: ${setContent::class.simpleName}")
                    }
                    makeTypeAlias(toClassName(name), "Set<$typeStr>")
                }

                else -> throw DetailedException("Unexpected datatype definition: ${def::class.simpleName}")
            }
        }.joinToString("\n")

        return "$superClass\n$clss"
    }
}

