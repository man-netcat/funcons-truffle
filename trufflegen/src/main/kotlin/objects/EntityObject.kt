package main.objects

import main.*
import main.dataclasses.Param
import org.antlr.v4.runtime.tree.ParseTree

open class EntityObject(
    name: String,
    ctx: ParseTree,
    params: List<Param>,
    aliases: List<String>,
    metaVariables: Set<Pair<String, String>>,
    private val entityType: EntityType
) : Object(name, ctx, params, aliases, metaVariables) {
    private val entityClassName
        get() = when (entityType) {
            EntityType.CONTEXTUAL -> CONTEXTUALENTITY
            EntityType.CONTROL -> CONTROLENTITY
            EntityType.MUTABLE -> MUTABLEENTITY
        }

    override val annotations: List<String>
        get() = listOf("Entity")

    override val superClassStr: String
        get() = makeFunCall(entityClassName, listOf("p0"))
}