package main.visitors

import cbs.CBSBaseVisitor
import cbs.CBSParser.*

class CBSDependencyVisitor : CBSBaseVisitor<Unit>() {
    val dependencies = mutableSetOf<String>()

    override fun visitFunconExpression(ctx: FunconExpressionContext) {
        dependencies.add(ctx.name.text)
        return super.visitFunconExpression(ctx)
    }

    override fun visitMapExpression(ctx: MapExpressionContext) {
        dependencies.add("map")
        return super.visitMapExpression(ctx)
    }

    override fun visitSetExpression(ctx: SetExpressionContext) {
        dependencies.add("set")
        return super.visitSetExpression(ctx)
    }

    override fun visitListExpression(ctx: ListExpressionContext) {
        dependencies.add("list")
        return super.visitListExpression(ctx)
    }

    override fun visitTupleExpression(ctx: TupleExpressionContext) {
        dependencies.add("tuple")
        return super.visitTupleExpression(ctx)
    }

    override fun visitLabel(ctx: LabelContext) {
        dependencies.add(ctx.name.text)
        return super.visitLabel(ctx)
    }
}