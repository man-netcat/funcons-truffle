package main.objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Param
import org.antlr.v4.runtime.tree.ParseTree

abstract class Object(val ctx: ParseTree) {
    init {
        println("Generating object ${name}...")
    }

    private val skipCriteria: Boolean
        get() = listOf(
            builtin,                                    // Builtins should be implemented manually
            name in listOf(
                "left-to-right", "right-to-left",       // Ambiguous semantics
                "choice",                               // Utilises random
                "sequential",                           // Param after sequence
                "some-element", "stuck", "abstraction", // No rules, implement manually
                "read",                                 // Annoying
            )
        ).contains(true)

    val builtin
        get() = when (ctx) {
            is FunconDefinitionContext -> ctx.modifier != null
            is TypeDefinitionContext -> ctx.modifier != null
            is DatatypeDefinitionContext -> ctx.modifier != null
            else -> false
        }

    val name get() = getObjectName(ctx)
    val aliases get() = getObjectAliases(ctx)
    val params get() = getParams(ctx)

    val dependencies = mutableSetOf<Object>()
    open val contentStr: String = ""
    open val annotations: List<String> = emptyList()
    open val superClassStr: String = emptySuperClass(TERMNODE)
    open val keyWords: List<String> = listOf("open")
    open val properties: List<String> = listOf()

    val sequenceIndex: Int get() = params.indexOfFirst { it.type.isSequence }
    val hasSequence: Boolean get() = sequenceIndex >= 0
    val sequenceParam: Param? get() = if (hasSequence) params[sequenceIndex] else null
    val paramsAfterSequence: Int get() = if (hasSequence) params.size - (sequenceIndex + 1) else 0

    private val valueParamStrs: List<String>
        get() {
            return params.map { param ->
                val annotation = makeAnnotation(
                    isVararg = param.type.isVararg,
                    isEntity = this !is EntityObject
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

    val nodeName get() = toClassName(name)
    val asVarName get() = toVariableName(name)
    val interfaceName get() = toInterfaceName(name)

    fun makeCode(): String {
        return if (!skipCriteria) makeClass(
            nodeName,
            content = listOf(
                contentStr
            ).joinToString("\n"),
            constructorArgs = valueParamStrs,
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
