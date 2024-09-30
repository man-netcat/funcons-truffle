package trufflegen.main

import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    internal open val context: FunconDefinitionContext,
    override val name: String,
    open val params: List<Param>,
    aliases: List<AliasDefinitionContext>,
    val metavariables: Map<ExprContext, ExprContext>,
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

        val mvarVisitor = MetavariableVisitor()
        val typeParams = metavariables.keys.map { key -> mvarVisitor.visit(key) }

        val content = makeContent()

        val cls = makeClass(nodeName, emptyList(), paramsStr, emptyList(), content, typeParams, "Computation")

        return cls
    }

    override fun generateBuiltinTemplate(): String {
        val paramsStr = params.map { param ->
            val annotation = if (param.type.isVararg) "@Children private vararg val" else "@Child private val"
            println(context.text)
            val paramTypeStr = buildTypeRewrite(param.type)
            Triple(annotation, param.name, paramTypeStr)
        }

        val cls = makeClass(nodeName, emptyList(), paramsStr, emptyList(), "TODO(\"Implement me\")")

        return cls
    }
}

