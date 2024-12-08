package main.objects

import cbs.CBSParser.*
import main.dataclasses.Param

class ControlEntityObject(
    name: String,
    ctx: ControlEntityDefinitionContext,
    params: List<Param>,
    private val polarity: String?,
    aliases: List<String>,
    metaVariables: MutableSet<Pair<String, String>>,
) : EntityObject(name, ctx, params, aliases, metaVariables)