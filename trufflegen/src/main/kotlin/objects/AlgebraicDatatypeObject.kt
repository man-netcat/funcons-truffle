package main.objects

import cbs.CBSParser
import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.getObject
import main.makeFunCall
import main.makeIsInTypeFunction
import main.toNodeName
import objects.DatatypeFunconObject

class AlgebraicDatatypeObject(
    ctx: DatatypeDefinitionContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
    val elementFuncons: List<CBSParser.FunconExpressionContext>,
) : Object(ctx, metaVariables) {
    val definitions = mutableListOf<DatatypeFunconObject>()
    override val keyWords: List<String>
        get() = listOf("open")
    override val superClassStr: String
        get() = makeFunCall(toNodeName("datatype-values"))
    val elementInBody: String
        get() {
            val classList = definitions.joinToString(",\n") { def ->
                "    Value${def.nodeName}::class"
            }
            val isInList = elementFuncons.joinToString(" || ") { funcon ->
                val obj = getObject(funcon)
                "this.isIn${obj.camelCaseName}()"
            }
            return makeIsInTypeFunction(
                camelCaseName, listOf(
                    "return this::class in setOf(\n$classList\n)",
                    isInList
                ).filter { it.isNotEmpty() }.joinToString(" || ")
            )
        }
}

