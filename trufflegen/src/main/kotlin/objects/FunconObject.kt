package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Rule
import main.dataclasses.Type
import main.exceptions.DetailedException
import main.exceptions.EmptyConditionException
import main.objects.AlgebraicDatatypeObject
import main.objects.EntityObject
import main.objects.Object
import org.antlr.v4.runtime.tree.ParseTree

abstract class AbstractFunconObject(
    ctx: ParseTree,
    metaVariables: Set<Pair<String, String>>,
) : Object(ctx, metaVariables) {
    val reducibleIndices = computeReducibles()
    override val properties = if (reducibleIndices.isNotEmpty()) {
        listOf(
            makeVariable(
                "reducibles",
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

    protected fun generateLocalVariables(): String = params.joinToString("\n") { param ->
        makeVariable("l${param.index}", value = "p${param.index}")
    }
}

class FunconObject(
    ctx: FunconDefinitionContext,
    metaVariables: Set<Pair<String, String>>,
    val returns: Type,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val rewritesTo: ExprContext? = null,
) : AbstractFunconObject(ctx, metaVariables) {
    override val contentStr: String
        get() {
            val stringBuilder = StringBuilder()

            val new = when {
                rewritesTo != null -> rewrite(ctx, rewritesTo)
                rules.isNotEmpty() -> {
                    val ruleObjs = rules.map { rule ->
                        Rule(rule.premises()?.premiseExpr()?.toList() ?: emptyList(), rule.conclusion, returns)
                    }
                    val entityVars = generateEntityVariables(ruleObjs)

                    val (ruleWithEmpty, rule) = ruleObjs.partition { it.emptyConditions.isNotEmpty() }
                    val emptyPairs = ruleWithEmpty.map { it.emptyConditions.joinToString(" && ") to it.bodyStr }
                    val pairs = rule.map { it.conditions.joinToString(" && ") to it.bodyStr }

                    if (entityVars.isNotEmpty()) stringBuilder.appendLine(entityVars)

                    val allPairs = emptyPairs + pairs

                    if (allPairs.any { pair -> pair.second.isEmpty() }) throw EmptyConditionException("Empty condition found in $name")

                    makeWhenStatement(allPairs, elseBranch = "abort(\"$name\")")
                }

                else -> throw DetailedException("Funcon $name does not have any associated rules.")
            }

            val newVar = makeVariable("new", new)
            val localVariables = generateLocalVariables()

            stringBuilder.appendLine(localVariables)
            stringBuilder.appendLine(newVar)
            stringBuilder.appendLine(returnStr)
            return makeReduceFunction(stringBuilder.toString(), TERMNODE)
        }

    override val annotations: List<String> get() = listOf("CBSFuncon")
    override val keyWords: List<String> = emptyList()
}

class DatatypeFunconObject(
    ctx: FunconExpressionContext,
    metaVariables: Set<Pair<String, String>>,
    private val superclass: AlgebraicDatatypeObject,
) : AbstractFunconObject(ctx, metaVariables) {
    override val annotations: List<String> get() = listOf("CBSFuncon")
    override val superClassStr: String get() = makeFunCall(if (reducibleIndices.isEmpty()) superclass.nodeName else TERMNODE)
    override val keyWords: List<String> = emptyList()

    override val contentStr: String
        get() {
            return if (reducibleIndices.isNotEmpty()) {
                val new = "Value$nodeName(${params.joinToString { param -> "l${param.index}" }})"
                val localVariables = generateLocalVariables()
                val newVar = makeVariable("new", new)

                val reduceBuilder = StringBuilder()
                reduceBuilder.appendLine(localVariables)
                reduceBuilder.appendLine(newVar)
                reduceBuilder.appendLine(returnStr)
                makeReduceFunction(reduceBuilder.toString(), TERMNODE)
            } else ""
        }
}
