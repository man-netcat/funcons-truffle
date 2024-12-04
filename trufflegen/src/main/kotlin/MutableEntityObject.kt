package main

import antlr.CBSParser.*

class MutableEntityObject(
    name: String,
    ctx: MutableEntityDefinitionContext,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: MutableSet<Pair<String, String>>
) : EntityObject(name, ctx, params, aliases, metaVariables)