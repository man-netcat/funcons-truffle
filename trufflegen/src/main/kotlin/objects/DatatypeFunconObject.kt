package main.objects

import cbs.CBSParser.FunconExpressionContext
import main.makeFunCall

class DatatypeFunconObject(
    ctx: FunconExpressionContext,
    metaVariables: Set<Pair<String, String>>,
    private val superclass: AlgebraicDatatypeObject
) : Object(ctx, metaVariables) {
    override val annotations: List<String>
        get() = listOf("CBSFuncon")
    override val superClassStr: String
        get() = makeFunCall(superclass.nodeName)
    override val keyWords: List<String> = emptyList()
}