package main.objects

import antlr.CBSParser.*
import main.*

class MutableEntityObject(
    name: String,
    ctx: MutableEntityDefinitionContext,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: MutableSet<Pair<String, String>>
) : EntityObject(name, ctx, params, aliases, metaVariables)