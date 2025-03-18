package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Rule
import main.dataclasses.Type
import main.exceptions.DetailedException
import main.objects.AlgebraicDatatypeObject
import main.objects.EntityObject
import main.objects.Object
import org.antlr.v4.runtime.tree.ParseTree

abstract class AbstractFunconObject(
    ctx: ParseTree,
    metaVariables: Set<Pair<String, String>>,
) : Object(ctx, metaVariables) {

    protected val returnStr = "return replace(new)"

    protected fun computeReducibles(): List<Int> =
        params.mapIndexedNotNull { index, param -> if (!param.type.computes) index else null }

    protected fun generateReduceComputations(reducibles: List<Int>): String =
        if (reducibles.isNotEmpty()) {
            "reduceComputations(frame, listOf(${reducibles.joinToString()}))?.let { reduced -> return replace(reduced) }"
        } else ""

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
    metaVariables: Set<Pair<String, String>>,
    val returns: Type,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val rewritesTo: ExprContext? = null,
) : AbstractFunconObject(ctx, metaVariables) {
    override val contentStr: String
        get() {
            val reducibles = computeReducibles()
            val reduceComputations = generateReduceComputations(reducibles)
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

                    makeWhenStatement(emptyPairs + pairs, elseBranch = "abort(\"$name\")")
                }

                else -> throw DetailedException("Funcon $name does not have any associated rules.")
            }

            if (reduceComputations.isNotEmpty()) stringBuilder.appendLine(reduceComputations)
            val newVar = makeVariable("new", new)
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
    val reducibles = computeReducibles()

    override val annotations: List<String> get() = listOf("CBSFuncon")
    override val superClassStr: String get() = makeFunCall(if (reducibles.isEmpty()) superclass.nodeName else TERMNODE)
    override val keyWords: List<String> = emptyList()

    override val contentStr: String
        get() {
            return if (reducibles.isNotEmpty()) {
                val reduceComputations = generateReduceComputations(reducibles)
                val new = "Value$nodeName(${params.joinToString { it.name }})"
                val newVar = makeVariable("new", new)

                val stringBuilder = StringBuilder()

                if (reduceComputations.isNotEmpty()) stringBuilder.appendLine(reduceComputations)

                stringBuilder.appendLine(newVar)
                stringBuilder.appendLine(returnStr)
                makeReduceFunction(stringBuilder.toString(), TERMNODE)
            } else ""
        }
}
