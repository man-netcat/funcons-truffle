package trufflegen.main

import trufflegen.antlr.CBSParser.*

abstract class FunconObject(
    internal open val context: FunconDefinitionContext,
    override val name: String,
    open val params: List<Param>,
    private val returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
) : Object(aliases) {

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
            Triple(annotation, param.name, "FCTNode")
        }

        val content = "return " + makeContent()

        val cls = makeClass(nodeName, emptyList(), paramsStr, emptyList(), content)

        return cls
    }
}

