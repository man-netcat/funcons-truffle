package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Rule
import main.exceptions.DetailedException
import main.objects.AlgebraicDatatypeObject
import main.objects.EntityObject
import main.objects.Object
import org.antlr.v4.runtime.tree.ParseTree

abstract class AbstractFunconObject(ctx: ParseTree) : Object(ctx) {
    val reducibleIndices = computeReducibles()
    override val keyWords: List<String> = listOf()

    protected fun computeReducibles(): List<Int> =
        params.mapIndexedNotNull { index, param -> if (!param.type.computes) index else null }

    protected fun generateEntityVariables(ruleObjs: List<Rule>): String =
        buildString {
            ruleObjs.flatMap { it.entityVars }.distinct().forEach { entityObj ->
                entityObj as EntityObject
                appendLine(makeVariable(entityObj.asVarName, entityObj.getStr()))
            }
        }
}

class FunconObject(
    ctx: FunconDefinitionContext,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val rewritesTo: ExprContext? = null,
    val metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : AbstractFunconObject(ctx) {
    override val contentStr: String
        get() {
            val stringBuilder = StringBuilder()

            val returnStr = "return " + when {
                rewritesTo != null -> rewrite(ctx, rewritesTo)
                rules.isNotEmpty() -> {
                    val outerVariables = mutableMapOf<String, String>()
                    val ruleObjs = rules.map { rule ->
                        val premises = rule.premises()?.premiseExpr()?.toList() ?: emptyList()
                        Rule(premises, rule.conclusion, metaVariables, outerVariables)
                    }
                    val entityVars = generateEntityVariables(ruleObjs)

                    val pairs = ruleObjs.sortedBy { it.rulePriority }
                        .map { it.getSortedConditions().joinToString(" && ") to it.bodyStr }

                    if (entityVars.isNotEmpty()) stringBuilder.appendLine(entityVars)

                    outerVariables.forEach { (rewrite, varName) ->
                        val rewriteStr = makeVariable(varName, "$rewrite.rewrite(frame)")
                        stringBuilder.appendLine(rewriteStr)
                    }

                    makeWhenStatement(pairs, elseBranch = "FailNode()")
                }

                else -> throw DetailedException("Funcon $name does not have any associated rules.")
            }

            stringBuilder.appendLine(returnStr)
            return makeReduceFunction(stringBuilder.toString(), TERMNODE)
        }

    override val keyWords: List<String> = emptyList()
}

class DatatypeFunconObject(
    ctx: FunconExpressionContext,
    internal val superclass: AlgebraicDatatypeObject,
) : AbstractFunconObject(ctx) {
    override val superClassStr: String get() = makeFunCall(if (reducibleIndices.isEmpty()) superclass.nodeName else TERMNODE)
    override val keyWords: List<String> = emptyList()

    override val contentStr: String
        get() {
            return if (reducibleIndices.isNotEmpty()) {
                val reduceBuilder = StringBuilder()
                val returnStr = "return Value$nodeName(${params.joinToString { param -> "p${param.index}" }})"
                reduceBuilder.appendLine(returnStr)
                makeReduceFunction(reduceBuilder.toString(), TERMNODE)
            } else ""
        }
}
