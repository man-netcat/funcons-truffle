package main.objects

import main.*
import org.antlr.v4.runtime.tree.ParseTree

open class EntityObject(
    ctx: ParseTree,
    metaVariables: Set<Pair<String, String>>,
    private val entityType: EntityType,
) : Object(ctx, metaVariables) {
    private val entityClassMap = mapOf(
        EntityType.CONTEXTUAL to CONTEXTUALENTITY,
        EntityType.CONTROL to CONTROLENTITY,
        EntityType.MUTABLE to MUTABLEENTITY,
        EntityType.INPUT to INPUTENTITY,
        EntityType.OUTPUT to OUTPUTENTITY
    )

    private val entityClassName
        get() = entityClassMap[entityType]!!

    val getFunc = when (entityType) {
        EntityType.CONTEXTUAL -> ::getInScopeStr
        else -> ::getGlobalStr
    }

    val putFunc = when (entityType) {
        EntityType.CONTEXTUAL -> ::putInScopeStr
        else -> ::putGlobalStr
    }

    fun getStr(): String {
        val qmark = if (entityType == EntityType.CONTROL) "?" else ""
        return "(${getFunc(name)} as $nodeName$qmark)?"
    }

    fun putStr(value: String) = putFunc(name, value)

    val isIOEntity get() = entityType in listOf(EntityType.INPUT, EntityType.OUTPUT)

    override val annotations: List<String>
        get() = listOf("CBSEntity")

    override val superClassStr: String
        get() {
            return makeFunCall(entityClassName, listOf("p0"))
        }
}