package main.visitors

import cbs.CBSBaseVisitor
import cbs.CBSParser.FunconExpressionContext
import cbs.CBSParser.LabelContext

class CBSDependencyVisitor : CBSBaseVisitor<Unit>() {
    val dependencies = mutableSetOf<String>()

    override fun visitFunconExpression(ctx: FunconExpressionContext) {
        dependencies.add(ctx.name.text)
        super.visitFunconExpression(ctx)
    }

    override fun visitLabel(ctx: LabelContext) {
        dependencies.add(ctx.name.text)
        super.visitLabel(ctx)
    }
}