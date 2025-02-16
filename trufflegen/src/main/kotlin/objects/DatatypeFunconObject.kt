package main.objects

import cbs.CBSParser.FunconExpressionContext
import main.dataclasses.Param
import main.makeFunCall
import main.todoExecute

class DatatypeFunconObject(
    name: String,
    ctx: FunconExpressionContext,
    params: List<Param>,
    metaVariables: Set<Pair<String, String>>,
    private val superclass: AlgebraicDatatypeObject
) : Object(name, ctx, params, emptyList(), metaVariables) {
    override val annotations: List<String>
        get() = listOf("Funcon")
    override val superClassStr: String
        get() = makeFunCall(superclass.nodeName)
    override val keyWords: List<String> = emptyList()
    override val contentStr: String
        get() = todoExecute("Any")
}