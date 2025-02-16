package main.visitors

import cbs.CBSBaseVisitor
import cbs.CBSParser.*

class DependencyVisitor : CBSBaseVisitor<Unit>() {
    val dependencies = mutableListOf<String>()

    override fun visitFunconExpression(ctx: FunconExpressionContext) {
        dependencies.add(ctx.name.text)
        super.visitFunconExpression(ctx)
    }

    override fun visitLabel(ctx: LabelContext) {
        dependencies.add(ctx.name.text)
        super.visitLabel(ctx)
    }
}