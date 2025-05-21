package objects

import cbs.CBSParser
import main.TERMNODE
import main.makeFunCall
import main.makeReduceFunction
import main.objects.AlgebraicDatatypeObject
import main.objects.Object
import main.strStr

class DatatypeFunconObject(
    ctx: CBSParser.FunconExpressionContext,
    internal val superclass: AlgebraicDatatypeObject,
    metaVariables: Set<Pair<CBSParser.ExprContext, CBSParser.ExprContext>>,
) : Object(ctx, metaVariables) {
    override val superClassStr: String get() = makeFunCall(TERMNODE)

    override val contentStr: String
        get() {
            val reduceBuilder = StringBuilder()
            val paramStr = params.map { param -> "p${param.index}" }
            val cache = makeFunCall(
                "ValueNodeFactory.datatypeValueNode",
                listOf(strStr(name), "SequenceNode(${paramStr.joinToString()})")
            )
            val ctor = makeFunCall("Value$nodeName", paramStr)
            val returnStr = "return $cache { $ctor }"

            reduceBuilder.appendLine(returnStr)
            return makeReduceFunction(reduceBuilder.toString(), TERMNODE)
        }
}