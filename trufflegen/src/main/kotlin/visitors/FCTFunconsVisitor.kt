package main.visitors

import fct.FCTBaseVisitor
import fct.FCTParser

class FCTFunconsVisitor : FCTBaseVisitor<Unit>() {
    val fileFuncons = mutableSetOf<String>()
    override fun visitFunconExpression(ctx: FCTParser.FunconExpressionContext) {
        fileFuncons.add(ctx.name.text)
        super.visitFunconExpression(ctx)
    }
}
