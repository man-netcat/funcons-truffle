package main.objects

import main.*
import objects.FunconObject
import org.antlr.v4.runtime.tree.ParseTree

abstract class Object(
    val ctx: ParseTree,
    val metaVariables: Set<Pair<String, String>>,
) {
    val name get() = getObjectName(ctx)
    val aliases get() = getObjectAliases(ctx)
    val params get() = getParams(ctx)

    val dependencies = mutableSetOf<Object>()
    open val contentStr: String = ""
    open val annotations: List<String> = emptyList()
    open val superClassStr: String = emptySuperClass(FCTNODE)
    open val keyWords: List<String> = listOf("open")

    val varargParamIndex: Int get() = params.indexOfFirst { it.type.isVararg }
    val hasVararg: Boolean get() = varargParamIndex >= 0
    val paramsAfterVararg: Int get() = if (varargParamIndex >= 0) params.size - (varargParamIndex + 1) else 0

    private val isFuncon get() = this is FunconObject || this is DatatypeFunconObject
    private val isEntity get() = this is EntityObject
    val hasNullable get() = params.size == 1 && params[0].type.isNullable

    private val valueParamStrs: List<String>
        get() {
            val valueParams = params.filterNot { param -> param.valueExpr == null }

            return valueParams.map { param ->
                val annotation = makeAnnotation(
                    isVararg = param.type.isVararg,
                    isEntity = this !is EntityObject
                )
//                val paramTypeStr = if (param.type.computes) {
//                    param.type.rewrite(inNullableExpr = true, full = false)
//                } else FCTNODE
                val paramTypeStr = FCTNODE
                makeParam(paramTypeStr, param.name, annotation)
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
            content = listOf(
                contentStr
            ).joinToString("\n"),
            constructorArgs = valueParamStrs,
            superClass = superClassStr,
            annotations = annotations,
            typeParams = metaVariables,
            keywords = keyWords,
        )
}
