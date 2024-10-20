package trufflegen.main

import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    name: String,
    ctx: FunconDefinitionContext,
    val params: List<Param>,
    val returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    metavariables: Map<ExprContext, ExprContext>,
    val builtin: Boolean,
) : Object(name, ctx, params, aliases, metavariables) {

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
            makeParam(annotation, param.name, paramTypeStr)
        }

        val (typeParams, content) = if (builtin) {
            emptySet<String>() to todoExecute()
        } else {
            makeTypeParams() to makeContent()
        }

        return makeClass(
            nodeName,
            content = content,
            constructorArgs = paramsStr,
            typeParams = typeParams,
            superClass = "Computation"
        )
    }
}

