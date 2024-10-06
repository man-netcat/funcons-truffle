package trufflegen.main

import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    internal open val context: FunconDefinitionContext,
    override val name: String,
    open val params: List<Param>,
    aliases: List<AliasDefinitionContext>,
    metavariables: Map<ExprContext, ExprContext>,
    val builtin: Boolean,
) : Object(aliases, metavariables, builtin) {

    private val paramsAfterVarargs: Int
        get() {
            val varargParamIndex = params.indexOfFirst { it.type.isVararg }
            return params.size - varargParamIndex
        }

    private val numVarargs: Int
        get() {
            return params.count { param -> param.type.isVararg }
        }

    abstract fun makeContent(): String

    override fun generateCode(): String {
        val paramsStr = params.map { param ->
            val annotation = if (param.type.isVararg) "@Children private vararg val" else "@Child private val"
            val paramTypeStr = buildTypeRewrite(param.type)
            Triple(annotation, param.name, paramTypeStr)
        }

        return if (builtin) {
            makeClass(
                nodeName,
                content = "TODO(\"Implement me\")",
                constructorArgs = paramsStr,
                superClass = "Computation"
            )
        } else {
            val typeParams = makeTypeParams()

            val content = makeContent()

            makeClass(
                nodeName,
                content = content,
                constructorArgs = paramsStr,
                typeParams = typeParams,
                superClass = "Computation"
            )
        }
    }
}

