package main.objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Param
import objects.DatatypeFunconObject
import org.antlr.v4.runtime.tree.ParseTree

abstract class Object(val ctx: ParseTree, metaVariables: Set<Pair<ExprContext, ExprContext>>) {
    init {
        println("Generating object ${name}...")
    }

    val metavariableMap = metaVariables.associate { (variable, type) -> variable.text to type }

    val builtin: Boolean
        get() = name in builtinOverride || when (ctx) {
            is FunconDefinitionContext -> ctx.modifier?.text == "Built-in"
            is TypeDefinitionContext -> ctx.modifier?.text == "Built-in"
            is DatatypeDefinitionContext -> ctx.modifier?.text == "Built-in"
            else -> false
        } || (this is DatatypeFunconObject && this.superclass.builtin)

    val name get() = getObjectName(ctx)
    val aliases get() = getObjectAliases(ctx)
    val params get() = getParams(ctx)

    val dependencies = mutableSetOf<Object>()
    open val contentStr: String = ""
    open val annotations: List<String> = emptyList()
    open val superClassStr: String = emptySuperClass(TERMNODE)
    open val keyWords: List<String> = emptyList()
    open val properties: List<String> = listOf()

    val sequenceIndex: Int get() = params.indexOfFirst { it.type.isSequence }
    val hasSequence: Boolean get() = sequenceIndex >= 0
    val sequenceParam: Param? get() = if (hasSequence) params[sequenceIndex] else null
    val paramsAfterSequence: Int get() = if (hasSequence) params.size - (sequenceIndex + 1) else 0

    private val paramStrs: List<String>
        get() {
            return params.map { param ->
                val annotation = makeAnnotation(
                    isVararg = param.type.isVararg,
                    isEntity = this !is EntityObject,
                    isEager = !param.type.computes
                )
                val paramTypeStr = if (param.type.isSequence) SEQUENCE else TERMNODE
                val default = if (param.type.isOptional || param.type.isSequence) "SequenceNode()" else ""
                val paramName =
                    if (this is TypeObject || this is AlgebraicDatatypeObject) "tp${param.index}" else "p${param.index}"
                makeParam(paramTypeStr, paramName, annotation, default)
            }
        }

    private val interfaceProperties: List<String>
        get() {
            val valueParams = params.filterNot { param -> param.valueExpr == null }

            return valueParams.map { param ->
                val paramTypeStr = if (param.type.isSequence) {
                    if (param.type.isVararg) {
                        "Array<out $SEQUENCE>"
                    } else SEQUENCE
                } else TERMNODE
                makeVariable("p${param.index}", type = paramTypeStr)
            }
        }

    val camelCaseName = toCamelCase(name)
    val nodeName = toNodeName(name)
    val interfaceName = toInterfaceName(name)
    val asVarName = toVariableName(name)

    fun makeCode(): String {
        return if (!builtin) makeClass(
            if (this is EntityObject) entityName else nodeName,
            content = listOf(
                contentStr
            ).joinToString("\n"),
            constructorArgs = paramStrs,
            superClass = superClassStr,
            annotations = annotations,
            keywords = keyWords,
            properties = properties,
        ) else makeInterface(
            interfaceName,
            properties = interfaceProperties,
            annotations = annotations,
        )
    }
}
