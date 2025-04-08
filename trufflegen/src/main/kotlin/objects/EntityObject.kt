package main.objects

import main.*
import org.antlr.v4.runtime.tree.ParseTree

open class EntityObject(
    ctx: ParseTree,
    val entityType: EntityType,
) : Object(ctx) {
    private val entityClassMap = mapOf(
        EntityType.CONTEXTUAL to CONTEXTUALENTITY,
        EntityType.CONTROL to CONTROLENTITY,
        EntityType.MUTABLE to MUTABLEENTITY,
        EntityType.INPUT to INPUTENTITY,
        EntityType.OUTPUT to OUTPUTENTITY
    )

    val entityName: String = toEntityName(name)
    val varName: String = toVariableName(name)

    private val entityClassName
        get() = entityClassMap[entityType]!!

    fun getStr(): String {
        val getFunc = when (entityType) {
            EntityType.CONTEXTUAL -> ::getInScopeStr
            else -> ::getGlobalStr
        }
        return getFunc(name)
    }

    fun putStr(value: String): String {
        val putFunc = when (entityType) {
            EntityType.CONTEXTUAL -> ::putInScopeStr
            EntityType.OUTPUT, EntityType.INPUT -> ::appendGlobalStr
            else -> ::putGlobalStr
        }
        return putFunc(name, value)
    }

    override val superClassStr: String
        get() {
            return makeFunCall(entityClassName, listOf(if (!params[0].type.isSequence) "p0.toSequence()" else "p0"))
        }
}