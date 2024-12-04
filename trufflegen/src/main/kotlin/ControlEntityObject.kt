package main

import antlr.CBSParser.*

class ControlEntityObject(
    name: String,
    ctx: ControlEntityDefinitionContext,
    params: List<Param>,
    private val polarity: String?,
    aliases: List<String>,
    metaVariables: MutableSet<Pair<String, String>>,
) : EntityObject(name, ctx, params, aliases, metaVariables)