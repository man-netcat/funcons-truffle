package main.objects

import antlr.CBSParser.*
import main.*

class ControlEntityObject(
    name: String,
    ctx: ControlEntityDefinitionContext,
    params: List<Param>,
    private val polarity: String?,
    aliases: List<String>,
    metaVariables: MutableSet<Pair<String, String>>,
) : EntityObject(name, ctx, params, aliases, metaVariables)