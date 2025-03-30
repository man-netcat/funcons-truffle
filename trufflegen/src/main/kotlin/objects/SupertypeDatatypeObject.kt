package main.objects

import cbs.CBSParser.DatatypeDefinitionContext
import cbs.CBSParser.ExprContext
import main.exceptions.DetailedException
import main.rewrite

class SupertypeDatatypeObject(
    ctx: DatatypeDefinitionContext,
    private val definitions: List<ExprContext>
) : Object(ctx) {
    override val superClassStr: String
        get() = if (definitions.size == 1) {
            rewrite(ctx, definitions[0])
        } else throw DetailedException("Has more than one superclass")
}