package main.objects

import cbs.CBSParser.*
import main.dataclasses.Param

class ContextualEntityObject(
    name: String,
    ctx: ContextualEntityDefinitionContext,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : EntityObject(name, ctx, params, aliases, metaVariables)