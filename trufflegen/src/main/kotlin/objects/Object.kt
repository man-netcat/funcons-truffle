package main.objects

import main.*
import org.antlr.v4.runtime.tree.ParseTree

abstract class Object(
    val name: String,
    val ctx: ParseTree,
    val params: List<Param>,
    private val aliases: List<String>,
    val metaVariables: Set<Pair<String, String>>,
) {
    abstract fun generateCode(): String

    val varargParamIndex: Int = params.indexOfFirst { it.type.isVararg }
    val paramsSize: Int = params.size
    val paramsAfterVararg: Int = paramsSize - (varargParamIndex + 1)

    val valueParams = params.filter { it.value != null }.map { param ->
        val annotation = param.type.annotation
        val paramTypeStr = buildTypeRewrite(param.type, nullable = false)
        makeParam(annotation, param.name, paramTypeStr)
    }

    fun aliasStr(): String {
        return aliases.joinToString("\n") { alias ->
            makeTypeAlias(toClassName(alias), nodeName, metaVariables)
        }
    }

    internal val nodeName: String
        get() = toClassName(name)
}
