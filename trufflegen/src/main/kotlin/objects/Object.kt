package main.objects

import main.*
import objects.FunconObject
import org.antlr.v4.runtime.tree.ParseTree

abstract class Object(
    val ctx: ParseTree,
    private val metaVariables: Set<Pair<String, String>>,
) {
    val name get() = getObjectName(ctx)
    val aliases get() = getObjectAliases(ctx)
    val params get() = getParams(ctx)

    val dependencies = mutableSetOf<Object>()
    open val contentStr: String = ""
    open val annotations: List<String> = emptyList()
    open val superClassStr: String = emptySuperClass(FCTNODE)
    open val keyWords: List<String> = listOf("open")

    private val nameStr = makeVariable("typeName", strStr(name), override = true)

    val varargParamIndex: Int get() = params.indexOfFirst { it.type.isVararg }
    val hasVararg: Boolean get() = varargParamIndex >= 0
    val paramsAfterVararg: Int get() = if (varargParamIndex >= 0) params.size - (varargParamIndex + 1) else 0

    private val isFuncon get() = this is FunconObject || this is DatatypeFunconObject
    private val isEntity get() = this is EntityObject
    val hasNullable get() = params.size == 1 && params[0].type.isNullable

    private val valueParamStrs: List<String>
        get() {
            return params.map { param ->
                val annotation = makeAnnotation(param.type.isVararg)
                val paramTypeStr = param.type.rewrite(inNullableExpr = true, full = false)
                makeParam(annotation, param.name, paramTypeStr)
            }
        }

    val aliasStr
        get() = aliases.joinToString("\n") { alias ->
            makeTypeAlias(toClassName(alias), nodeName, typeParams = metaVariables)
        }

    val nodeName = toClassName(name)

    val code
        get() = makeClass(
            nodeName,
            content = "${nameStr}\n$contentStr",
            constructorArgs = valueParamStrs,
            superClass = superClassStr,
            annotations = annotations,
            typeParams = metaVariables,
            keywords = keyWords,
        )
}
