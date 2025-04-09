package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.makeFunCall
import main.makeIsInTypeFunction
import main.toNodeName
import objects.DatatypeFunconObject

class AlgebraicDatatypeObject(
    ctx: DatatypeDefinitionContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : Object(ctx, metaVariables) {
    val definitions = mutableListOf<DatatypeFunconObject>()
    override val keyWords: List<String>
        get() = listOf("open")
    override val superClassStr: String
        get() = makeFunCall(toNodeName("datatype-values"))
    val elementInBody: String
        get() = if (!builtin) {
            val classList = definitions.joinToString(",\n") { def ->
                val explicitValue = if (def.params.isNotEmpty()) "Value" else ""
                "    $explicitValue${def.nodeName}::class"
            }
            makeIsInTypeFunction(camelCaseName, "return this::class in setOf(\n$classList\n)")
        } else ""
}

