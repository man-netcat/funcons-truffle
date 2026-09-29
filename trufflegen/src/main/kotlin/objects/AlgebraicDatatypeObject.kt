package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.Registry
import main.makeFunCall
import main.makeIsInTypeFunction
import main.makeTypeCondition
import main.withSubtypesOf
import main.toNodeName
import objects.DatatypeFunconObject

class AlgebraicDatatypeObject(
    ctx: DatatypeDefinitionContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
    registry: Registry,
) : Object(ctx, metaVariables, registry) {
    val definitions = mutableListOf<DatatypeFunconObject>()
    private val definitionExpr = ctx.definition
    override val keyWords: List<String>
        get() = listOf("open")
    override val superClassStr: String
        get() = makeFunCall(toNodeName("datatype-values"))
    override val elementInBody: String
        get() {
            val conditionStr = makeTypeCondition(registry, "this", definitionExpr)
            return makeIsInTypeFunction(camelCaseName, "return ${withSubtypesOf(registry, name, conditionStr)}")
        }
}

