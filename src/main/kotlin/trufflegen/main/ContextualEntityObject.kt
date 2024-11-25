package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext
import trufflegen.antlr.CBSParser.ContextualEntityDefinitionContext

class ContextualEntityObject(
    name: String,
    ctx: ContextualEntityDefinitionContext,
    params: List<Param>,
    aliases: List<AliasDefinitionContext>,
    metaVariables: Set<Pair<String, String>>
) : EntityObject(name, ctx, params, aliases, metaVariables)