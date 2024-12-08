package main.objects

import org.antlr.v4.runtime.tree.ParseTree
import main.*
import main.dataclasses.Param

open class EntityObject(
    name: String,
    ctx: ParseTree,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        return makeClass(
            name = nodeName,
            constructorArgs = valueParams,
            typeParams = emptyList(), // TODO Fix
            body = false,
            annotations = listOf("Entity")
        )
    }
}