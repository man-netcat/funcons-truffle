package main.objects

import cbs.CBSParser.FunconExpressionContext
import main.makeFunCall
import main.todoExecute

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
    override val contentStr: String
        get() = todoExecute("Any")
}