package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import main.dataclasses.Param
import main.makeFunCall
import main.toClassName

class AlgebraicDatatypeObject(
    name: String,
    ctx: DatatypeDefinitionContext,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    val definitions = mutableListOf<DatatypeFunconObject>()
    override val annotations: List<String>
        get() = listOf("DataType")
    override val keyWords: List<String>
        get() = listOf("abstract")
    override val superClassStr: String
        get() = makeFunCall(toClassName("datatype-values"))
}

