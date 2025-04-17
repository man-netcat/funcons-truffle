package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.rewrite

class SupertypeDatatypeObject(
    ctx: DatatypeDefinitionContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : Object(ctx, metaVariables) {
    val definition: ExprContext = ctx.definition
    override val superClassStr: String
        get() = rewrite(ctx, definition, isTypeParam = true)
}