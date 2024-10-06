package trufflegen.main

import trufflegen.antlr.CBSParser.*

class DatatypeObject(
    private val datatype: DatatypeDefinitionContext,
    override val name: String,
    private val params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    val builtin: Boolean,
) : Object(aliases, metavariables, builtin) {
    override fun generateCode(): String {
        println("datatype: ${datatype.text}")

        if (builtin) return makeClass(name, content = "TODO(\"Implement me\")")

        val typeParams = makeTypeParams()

        val superClass = makeClass(
            toClassName(name), content = "mainclscontent: TODO", superClass = "Computation"
        )

        val clss = definitions.map { def ->
            println("def: ${def.text}")
            when (def) {
                is FunconExpressionContext -> {
                    val args = def.args()
                    println("args: ${args.text}, ${args::class.simpleName}")
                    when (args) {
                        is MultipleArgsContext -> {
                            assert(args.exprs().expr().size == 1)
                            val arg = args.exprs().expr(0)
                            val typeStr = when (arg) {
                                is TypeExpressionContext -> {
                                    val setType = ReturnType(arg.type)
                                    buildTypeRewrite(setType)
                                }

                                else -> throw DetailedException("Unexpected arg expression: ${arg::class.simpleName}")
                            }
                            makeClass(
                                toClassName(def.name.text),
                                content = "typecontent: TODO",
                                typeParams = typeParams,
                                superClass = toClassName(name)
                            )
                        }

                        is NoArgsContext -> {
                            makeClass(
                                toClassName(def.name.text),
                                typeParams = typeParams,
                                superClass = toClassName(name),
                                body = false
                            )
                        }

                        else -> throw DetailedException("Unexpected args expression: ${args::class.simpleName}")
                    }
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
