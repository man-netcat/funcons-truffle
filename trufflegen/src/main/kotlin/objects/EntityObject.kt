package main.objects

import main.dataclasses.Param
import main.makeClass
import main.makeFunCall
import org.antlr.v4.runtime.tree.ParseTree

open class EntityObject(
    name: String,
    ctx: ParseTree,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>,
    val entityClassName: String
) : Object(name, ctx, params, aliases, metaVariables) {
    override fun generateCode(): String {
        return makeClass(
            name = nodeName,
            constructorArgs = valueParams,
            typeParams = emptyList(), // TODO Fix
            body = false,
            annotations = listOf("Entity"),
            superClass = makeFunCall(
                entityClassName,
                listOf("p0")
            ),
        )
    }
}