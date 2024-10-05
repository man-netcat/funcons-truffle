package trufflegen.main

import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    internal open val context: FunconDefinitionContext,
    override val name: String,
    open val params: List<Param>,
    aliases: List<AliasDefinitionContext>,
    metavariables: Map<ExprContext, ExprContext>,
) : Object(aliases, metavariables) {

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

        val typeParams = makeTypeParams()

        val content = makeContent()

        val cls = makeClass(
            nodeName,
            content,
            constructorArgs = paramsStr,
            typeParams = typeParams,
            superClass = "Computation"
        )

        return cls
    }

    override fun generateBuiltinTemplate(): String {
        val paramsStr = params.map { param ->
            val annotation = if (param.type.isVararg) "@Children private vararg val" else "@Child private val"
            val paramTypeStr = buildTypeRewrite(param.type)
            Triple(annotation, param.name, paramTypeStr)
        }

        val cls = makeClass(nodeName, "TODO(\"Implement me\")", constructorArgs = paramsStr)

        return cls
    }
}

