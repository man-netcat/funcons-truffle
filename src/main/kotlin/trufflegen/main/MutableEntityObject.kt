package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext
import trufflegen.antlr.CBSParser.MutableEntityDefinitionContext

class MutableEntityObject(
    name: String,
    ctx: MutableEntityDefinitionContext,
    params: List<Param>,
    aliases: List<AliasDefinitionContext>,
    metaVariables: MutableSet<Pair<String, String>>
) : EntityObject(name, ctx, params, aliases, metaVariables)