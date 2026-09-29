package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.Registry
import main.rewrite

class SupertypeDatatypeObject(
    ctx: DatatypeDefinitionContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
    registry: Registry,
) : Object(ctx, metaVariables, registry) {
    val definition: ExprContext = ctx.definition
    override val superClassStr: String
        get() = rewrite(registry, ctx, definition)
}