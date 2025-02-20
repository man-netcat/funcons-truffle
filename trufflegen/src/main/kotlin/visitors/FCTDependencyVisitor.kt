package main.visitors

import fct.FCTBaseVisitor
import fct.FCTParser.*

class FCTDependencyVisitor : FCTBaseVisitor<Unit>() {
    val dependencies = mutableSetOf<String>()

    override fun visitFunconExpression(ctx: FunconExpressionContext) {
        dependencies.add(ctx.name.text)
        super.visitFunconExpression(ctx)
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
