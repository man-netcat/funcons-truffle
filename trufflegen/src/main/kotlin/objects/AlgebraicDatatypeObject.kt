package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.makeFunCall
import main.makeIsInTypeFunction
import main.makeTypeCondition
import main.toNodeName
import objects.DatatypeFunconObject

class AlgebraicDatatypeObject(
    ctx: DatatypeDefinitionContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : Object(ctx, metaVariables) {
    val definitions = mutableListOf<DatatypeFunconObject>()
    private val definitionExpr = ctx.definition
    override val keyWords: List<String>
        get() = listOf("open")
    override val superClassStr: String
        get() = makeFunCall(toNodeName("datatype-values"))
    val elementInBody: String
        get() {
            val conditionStr = makeTypeCondition("this", definitionExpr)
            return makeIsInTypeFunction(camelCaseName, "return $conditionStr")
        }
}

