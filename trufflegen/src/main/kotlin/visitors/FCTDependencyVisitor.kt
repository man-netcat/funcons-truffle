package main.visitors

import fct.FCTBaseVisitor
import fct.FCTParser.*

class FCTDependencyVisitor : FCTBaseVisitor<Unit>() {
    val dependencies = mutableSetOf<String>()

    override fun visitFunconExpression(ctx: FunconExpressionContext) {
        dependencies.add(ctx.name.text)
        super.visitFunconExpression(ctx)
    }

    override fun visitMapExpression(ctx: MapExpressionContext) {
        dependencies.add("map")
        super.visitMapExpression(ctx)
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

    override fun visitNumber(ctx: NumberContext) {
        dependencies.add("natural-numbers")
        super.visitNumber(ctx)
    }

    override fun visitString(ctx: StringContext) {
        dependencies.add("strings")
        super.visitString(ctx)
    }
}
