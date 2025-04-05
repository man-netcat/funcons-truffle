package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import main.exceptions.DetailedException
import main.makeFunCall
import main.makeValueTypesCompanionObject
import main.toClassName
import objects.DatatypeFunconObject

class AlgebraicDatatypeObject(ctx: DatatypeDefinitionContext) : Object(ctx) {
    val operator = ctx.op?.text
    val definitions = mutableListOf<DatatypeFunconObject>()
    override val keyWords: List<String>
        get() = listOf("open")
    override val superClassStr: String
        get() = makeFunCall(toClassName("datatype-values"))
    override val contentStr: String
        get() = when (operator) {
            null -> "" // Likely builtin
            "::=" -> {
                val classList = definitions.joinToString { def ->
                    val explicitValue = if (def.params.isNotEmpty()) "Value" else ""
                    "$explicitValue${def.nodeName}::class"
                }
                val body = "return value::class in setOf($classList)"
                makeValueTypesCompanionObject(body)
            }


            else -> throw DetailedException("Unexpected operator: $operator")
        }
}

