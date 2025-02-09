package main.objects

import cbs.CBSParser.ControlEntityDefinitionContext
import main.CONTROLENTITY
import main.dataclasses.Param

class ControlEntityObject(
    name: String,
    ctx: ControlEntityDefinitionContext,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: MutableSet<Pair<String, String>>,
) : EntityObject(name, ctx, params, aliases, metaVariables, CONTROLENTITY)