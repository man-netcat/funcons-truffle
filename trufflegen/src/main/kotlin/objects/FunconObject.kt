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
    override val properties = if (reducibleIndices.isNotEmpty()) {
        listOf(
            makeVariable(
                "eager",
                value = "listOf(${reducibleIndices.joinToString()})",
                override = true
            )
        )
    } else emptyList()

    protected val returnStr = "return replace(new)"

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

            val new = when {
                rewritesTo != null -> rewrite(ctx, rewritesTo)
                rules.isNotEmpty() -> {
                    val ruleObjs = rules.map { rule ->
                        Rule(rule.premises()?.premiseExpr()?.toList() ?: emptyList(), rule.conclusion, metaVariables)
                    }
                    val entityVars = generateEntityVariables(ruleObjs)

                    val pairs = ruleObjs.sortedBy { it.rulePriority }
                        .map { it.getSortedConditions().joinToString(" && ") to it.bodyStr }

                    if (entityVars.isNotEmpty()) stringBuilder.appendLine(entityVars)

                    makeWhenStatement(pairs, elseBranch = "FailNode()")
                }

                else -> throw DetailedException("Funcon $name does not have any associated rules.")
            }

            val newVar = makeVariable("new", new)
            stringBuilder.appendLine(newVar)
            stringBuilder.appendLine(returnStr)
            return makeReduceFunction(stringBuilder.toString(), TERMNODE)
        }

    override val keyWords: List<String> = emptyList()
}

class DatatypeFunconObject(
    ctx: FunconExpressionContext,
    private val superclass: AlgebraicDatatypeObject,
) : AbstractFunconObject(ctx) {
    override val superClassStr: String get() = makeFunCall(if (reducibleIndices.isEmpty()) superclass.nodeName else TERMNODE)
    override val keyWords: List<String> = emptyList()

    override val contentStr: String
        get() {
            return if (reducibleIndices.isNotEmpty()) {
                val new = "Value$nodeName(${params.joinToString { param -> "p${param.index}" }})"
                val newVar = makeVariable("new", new)

                val reduceBuilder = StringBuilder()
                reduceBuilder.appendLine(newVar)
                reduceBuilder.appendLine(returnStr)
                makeReduceFunction(reduceBuilder.toString(), TERMNODE)
            } else ""
        }
}
