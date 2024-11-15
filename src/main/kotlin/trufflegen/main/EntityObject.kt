package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSParser.AliasDefinitionContext

open class EntityObject(
    name: String,
    ctx: ParseTree,
    params: List<Param>,
    aliases: List<AliasDefinitionContext>,
    metavariables: Map<String, String>
) : Object(name, ctx, params, aliases, metavariables) {
    override fun generateCode(): String {
        return makeClass(
            name = nodeName,
            keywords = listOf("open"),
            constructorArgs = paramsStr,
            typeParams = typeParams,
            body = false
        )
    }
}