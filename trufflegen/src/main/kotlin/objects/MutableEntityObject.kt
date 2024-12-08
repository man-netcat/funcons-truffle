package main.objects

import cbs.CBSParser.*
import main.dataclasses.Param

class MutableEntityObject(
    name: String,
    ctx: MutableEntityDefinitionContext,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: MutableSet<Pair<String, String>>
) : EntityObject(name, ctx, params, aliases, metaVariables)