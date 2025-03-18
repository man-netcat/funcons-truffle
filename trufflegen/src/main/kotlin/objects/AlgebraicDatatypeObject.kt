package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import main.makeFunCall
import main.toClassName
import objects.DatatypeFunconObject

class AlgebraicDatatypeObject(
    ctx: DatatypeDefinitionContext,
    metaVariables: Set<Pair<String, String>>,
) : Object(ctx, metaVariables) {
    val definitions = mutableListOf<DatatypeFunconObject>()
    override val annotations: List<String>
        get() = listOf("CBSDataType")
    override val keyWords: List<String>
        get() = listOf("abstract")
    override val superClassStr: String
        get() = makeFunCall(toClassName("datatype-values"))
}

