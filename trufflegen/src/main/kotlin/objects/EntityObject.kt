package main.objects

import cbs.CBSParser
import main.getEntityStr
import main.putEntityStr
import main.toEntityName
import org.antlr.v4.runtime.tree.ParseTree

open class EntityObject(
    ctx: ParseTree,
    metaVariables: Set<Pair<CBSParser.ExprContext, CBSParser.ExprContext>>,
) : Object(ctx, metaVariables) {
    val entityName: String = toEntityName(name)
    fun getStr(): String = getEntityStr(name)
    fun putStr(value: String): String = putEntityStr(name, value)
}