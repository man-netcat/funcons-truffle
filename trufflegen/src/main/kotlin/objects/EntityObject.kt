package main.objects

import cbs.CBSParser
import main.*
import org.antlr.v4.runtime.tree.ParseTree

open class EntityObject(
    ctx: ParseTree,
    val entityType: EntityType,
    metaVariables: Set<Pair<CBSParser.ExprContext, CBSParser.ExprContext>>,
) : Object(ctx, metaVariables) {
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

    fun getStr(): String = getInScopeStr(name)

    fun putStr(value: String): String {
        val putFunc = when (entityType) {
            EntityType.OUTPUT, EntityType.INPUT -> ::appendGlobalStr
            else -> ::putInScopeStr
        }
        return putFunc(name, value)
    }

    override val superClassStr: String
        get() {
            return makeFunCall(entityClassName, listOf(if (!params[0].type.isSequence) "p0.toSequence()" else "p0"))
        }
}