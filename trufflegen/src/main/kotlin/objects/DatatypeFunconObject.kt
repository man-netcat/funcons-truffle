package main.objects

import cbs.CBSParser.FunconExpressionContext
import main.TERMNODE
import main.makeFunCall
import main.makeReduceFunction

class DatatypeFunconObject(
    ctx: FunconExpressionContext,
    metaVariables: Set<Pair<String, String>>,
    private val superclass: AlgebraicDatatypeObject,
) : Object(ctx, metaVariables) {
    val reducibles = params.mapIndexedNotNull { index, param -> index.takeIf { !param.type.computes } }

    override val annotations: List<String>
        get() = listOf("CBSFuncon")
    override val superClassStr: String
        get() = makeFunCall(if (reducibles.isEmpty()) superclass.nodeName else TERMNODE)
    override val keyWords: List<String> = emptyList()
    override val contentStr: String
        get() {
            return if (reducibles.isNotEmpty()) {
                val reduceComputations =
                    "reduceComputations(frame, listOf(${reducibles.joinToString()}))?.let { return replace(it) }\n\n"
                val body =
                    "$reduceComputations val new = Value$nodeName(${params.joinToString { it.name }})\nreturn replace(new)"
                makeReduceFunction(body, TERMNODE)
            } else ""
        }
}