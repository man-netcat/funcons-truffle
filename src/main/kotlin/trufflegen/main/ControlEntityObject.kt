package trufflegen.main

import trufflegen.antlr.CBSParser

class ControlEntityObject(
    name: String,
    ctx: CBSParser.ControlEntityDefinitionContext,
    params: List<Param>,
    private val polarity: String?,
    aliases: List<CBSParser.AliasDefinitionContext>,
    metaVariables: MutableSet<Pair<String, String>>,
) : EntityObject(name, ctx, params, aliases, metaVariables)