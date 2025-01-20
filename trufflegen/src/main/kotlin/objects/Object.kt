package main.objects

import main.*
import main.dataclasses.Param
import objects.FunconObject
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
    val paramsAfterVararg: Int = if (varargParamIndex >= 0) params.size - (varargParamIndex + 1) else 0

    val isFuncon = this is FunconObject || this is DatatypeFunconObject
    val isEntity = this is ControlEntityObject || this is MutableEntityObject || this is ContextualEntityObject
    val hasNullable = params.size == 1 && params[0].type.isNullable

    val valueParams = params.filter { it.value != null }.map { param ->
        val annotation = makeAnnotation(param.type.isVararg, isEntity)
        val paramTypeStr = rewriteType(param.type, nullable = true)
        makeParam(annotation, param.name, paramTypeStr)
    }

    fun aliasStr(): String {
        return aliases.joinToString("\n") { alias ->
            makeTypeAlias(toClassName(alias), nodeName)
        }
    }

    internal val nodeName: String
        get() = toClassName(name)
}
