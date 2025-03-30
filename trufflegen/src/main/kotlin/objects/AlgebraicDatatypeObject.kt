package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import main.makeFunCall
import main.toClassName
import objects.DatatypeFunconObject

class AlgebraicDatatypeObject(ctx: DatatypeDefinitionContext) : Object(ctx) {
    val definitions = mutableListOf<DatatypeFunconObject>()
    override val keyWords: List<String>
        get() = listOf("open")
    override val superClassStr: String
        get() = makeFunCall(toClassName("datatype-values"))
}

