package trufflegen.main

import trufflegen.antlr.CBSParser.*

class DatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    private val params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    val builtin: Boolean,
) : Object(name, ctx, params, aliases, metavariables) {

    fun argsToParams(args: List<ExprContext>): List<String> {
        return args.withIndex().map { (i, arg) ->
            when (arg) {
                is TypeExpressionContext -> {
                    val setType = ReturnType(arg.type)
                    val annotation = if (setType.isVararg) "@Children private vararg val" else "@Child private val"
                    val paramTypeStr = buildTypeRewrite(setType)
                    makeParam(annotation, "p$i", paramTypeStr)
                }

                else -> throw DetailedException("Unexpected arg expression: ${arg::class.simpleName}")
            }
        }
    }

    override fun generateCode(): String {
        val paramsStr = params.map { param ->
            val annotation = if (param.type.isVararg) "@Children private vararg val" else "@Child private val"
            val paramTypeStr = buildTypeRewrite(param.type)
            makeParam(annotation, param.name, paramTypeStr)
        }

        val typeParams = if (builtin) emptySet<String>() else makeTypeParams()

        val superClass = makeClass(
            nodeName,
            body = false,
            constructorArgs = paramsStr,
            keywords = listOf("open"),
            typeParams = typeParams,
            superClass = "Computation",
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

