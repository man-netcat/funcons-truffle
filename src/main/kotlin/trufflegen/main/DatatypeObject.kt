package trufflegen.main

import trufflegen.antlr.CBSParser.*

class DatatypeObject(
    private val datatype: DatatypeDefinitionContext,
    override val name: String,
    private val params: List<Param>,
    private val definitions: List<ExprContext>,
    aliases: MutableList<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
) : Object(aliases, metavariables) {
    override fun generateCode(): String {
        println("datatype: ${datatype.text}")
        val typeParams = makeTypeParams()

        val superClass = makeClass(
            toClassName(name),
            "mainclscontent: TODO",
            superClass = "Computation"
        )

        val clss = definitions.map { def ->
            println("def: ${def.text}")
            when (def) {
                is FunconExpressionContext -> {
                    makeClass(
                        toClassName(def.name.text),
                        "typecontent: TODO",
                        typeParams = typeParams,
                        superClass = toClassName(name)
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

    override fun generateBuiltinTemplate(): String {
        return makeClass(name, "TODO(\"Implement me\")")
    }
}
