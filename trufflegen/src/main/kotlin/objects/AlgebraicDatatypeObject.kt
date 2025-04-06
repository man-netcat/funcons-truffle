package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import main.exceptions.DetailedException
import main.makeFunCall
import main.makeIsInTypeFunction
import main.toNodeName
import objects.DatatypeFunconObject

class AlgebraicDatatypeObject(ctx: DatatypeDefinitionContext) : Object(ctx) {
    val operator = ctx.op?.text
    val definitions = mutableListOf<DatatypeFunconObject>()
    override val keyWords: List<String>
        get() = listOf("open")
    override val superClassStr: String
        get() = makeFunCall(toNodeName("datatype-values"))
    val makeElementInFunction: String
        get() = when (operator) {
            null -> "" // Likely builtin
            "::=" -> {
                val classList = definitions.joinToString { def ->
                    val explicitValue = if (def.params.isNotEmpty()) "Value" else ""
                    "$explicitValue${def.nodeName}::class"
                }
                makeIsInTypeFunction(camelCaseName, "return this::class in setOf($classList)")
            }


            else -> throw DetailedException("Unexpected operator: $operator")
        }
}

