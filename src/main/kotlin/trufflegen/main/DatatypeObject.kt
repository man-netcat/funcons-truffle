package trufflegen.main

import trufflegen.antlr.CBSParser.*

class DatatypeObject(
    name: String,
    private val params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    val builtin: Boolean,
) : Object(name, params, aliases) {

    fun argsToParams(args: List<ExprContext>): List<String> {
        return args.withIndex().map { (i, arg) ->
            when (arg) {
                is TypeExpressionContext -> {
                    val setType = ReturnType(arg.type)
                    val annotation = setType.annotation
                    val paramTypeStr = buildTypeRewrite(setType)
                    makeParam(annotation, "p$i", paramTypeStr)
                }

                else -> throw DetailedException("Unexpected arg expression: ${arg::class.simpleName}")
            }
        }
    }

    override fun generateCode(): String {
        val paramsStr = params.map { param ->
            val annotation = param.type.annotation
            val paramTypeStr = buildTypeRewrite(param.type)
            makeParam(annotation, param.name, paramTypeStr)
        }

        val superClass = makeClass(
            nodeName,
            body = false,
            constructorArgs = paramsStr,
            keywords = listOf("sealed"),
            superClasses = listOf(COMPUTATION to emptyList()),
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
                        constructorArgs = classParams,
                        superClasses = listOf(toClassName(name) to emptyList()),
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

