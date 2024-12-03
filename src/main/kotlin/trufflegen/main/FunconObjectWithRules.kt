package trufflegen.main

import trufflegen.antlr.CBSParser.*
import kotlin.collections.mapNotNull
import kotlin.collections.orEmpty

class FunconObjectWithRules(
    name: String,
    def: FunconDefinitionContext,
    params: List<Param>,
    private val rules: List<RuleDefinitionContext>,
    returns: Type,
    aliases: List<String>,
    builtin: Boolean,
    metaVariables: MutableSet<Pair<String, String>>
) : FunconObject(name, def, params, returns, aliases, builtin, metaVariables) {
    private fun complementStr(bool: Boolean): String = if (bool) "!" else ""
    private fun isInputOutputEntity(stepExpr: StepExprContext): Boolean {
        val steps = stepExpr.steps().step()
        val labels = steps.firstOrNull()?.labels()?.label()
        return steps.size == 1 && labels?.size == 1 && labels.firstOrNull()?.polarity != null
    }

    private fun gatherLabels(stepExpr: StepExprContext): List<LabelContext> {
        return if (stepExpr.context_?.name != null) {
            listOf(stepExpr.context_)
        } else {
            stepExpr.steps()?.step()?.sortedBy { step -> step.sequenceNumber?.text?.toInt() }?.flatMap { step ->
                step.labels()?.label()?.filter { label -> label.value != null }.orEmpty()
            }.orEmpty()
        }
    }

    private fun processConclusion(
        conclusion: PremiseContext
    ): Triple<ExprContext, String, String> {
        fun argsConditions(def: FunconExpressionContext): String {
            val paramStrs = getParamStrs(def)
            return paramStrs.mapNotNull { (argValue, argType, paramStr) ->
                val valueCondition = if (argValue != null) {
                    when (argValue) {
                        //TODO: This needs to be improved
                        is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext
                            -> "$paramStr is ${buildTypeRewrite(Type(argValue))}"

                        is NumberContext -> "$paramStr == ${argValue.text}"
                        is TupleExpressionContext -> "${paramStr}.isEmpty()"
                        is VariableContext, is SuffixExpressionContext -> null
                        else -> throw IllegalArgumentException("Unexpected arg type: ${argValue::class.simpleName}, ${argValue.text}")
                    }
                } else null

                val typeCondition = if (argType != null) {
                    val argType = Type(argType)
                    val rewritten = buildTypeRewrite(argType)
                    "$paramStr ${complementStr(argType.complement)}is $rewritten"
                } else null

                when {
                    typeCondition != null && valueCondition != null -> "$valueCondition && $typeCondition"
                    else -> typeCondition ?: valueCondition
                }
            }.joinToString(" && ")
        }

        return when (conclusion) {
            is RewritePremiseContext -> {
                val rewrite = buildRewrite(conclusion.lhs, conclusion.rhs)
                val conditions = if (conclusion.lhs is FunconExpressionContext) {
                    argsConditions(conclusion.lhs as FunconExpressionContext)
                } else ""
                Triple(conclusion.lhs, conditions, rewrite)
            }

            is StepPremiseContext -> {
                val stepExpr = conclusion.stepExpr()
                if (isInputOutputEntity(stepExpr)) return Triple(
                    stepExpr.lhs, "TODO(\"Condition\")", "TODO(\"Content\")"
                )
                val entityStr = gatherLabels(stepExpr).joinToString("\n") { label ->
                    entityMap(label.name.text) + " = " + if (label.value != null) {
                        buildRewrite(stepExpr.lhs, label.value)
                    } else "null"
                }
                var rewriteStr = buildRewrite(stepExpr.lhs, stepExpr.rhs)
                rewriteStr = if (entityStr.isNotEmpty()) "$entityStr\n$rewriteStr" else rewriteStr
                val conditions = if (stepExpr.lhs is FunconExpressionContext) {
                    argsConditions(stepExpr.lhs as FunconExpressionContext)
                } else ""
                Triple(stepExpr.lhs, conditions, rewriteStr)
            }

            is MutableEntityPremiseContext -> {
                val mutableExpr = conclusion.mutableExpr()
                val rewrite = buildRewrite(mutableExpr.lhs, mutableExpr.rhs)
                val conditions = if (mutableExpr.lhs is FunconExpressionContext) {
                    argsConditions(mutableExpr.lhs as FunconExpressionContext)
                } else ""
                Triple(mutableExpr.lhs, conditions, rewrite)
                // TODO: also modify the entity
            }

            else -> throw DetailedException("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }
    }

    private fun processPremises(
        premises: List<PremiseContext>, ruleDef: ExprContext
    ): Pair<String, String> {
        val result = premises.map { premise ->
            when (premise) {
                is StepPremiseContext -> {
                    val stepExpr = premise.stepExpr()
                    val labelConditions = gatherLabels(stepExpr).joinToString(" && ") { label ->
                        entityMap(label.name.text) + " == " + (if (label.value != null) {
                            buildRewrite(ruleDef, label.value)
                        } else "null")
                    }
                    val rewriteLhs = buildRewrite(ruleDef, stepExpr.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, stepExpr.rhs)
                    val condition = "$rewriteLhs is $COMPUTATION"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"

                    if (labelConditions.isNotBlank()) Pair("$condition && $labelConditions", rewrite)
                    else Pair(condition, rewrite)
                }

                is MutableEntityPremiseContext -> {
                    //TODO Fix
                    val mutableExpr = premise.mutableExpr()
                    val rewriteLhs = buildRewrite(ruleDef, mutableExpr.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, mutableExpr.rhs)
                    val condition = "$rewriteLhs is $COMPUTATION"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"

                    Pair(condition, rewrite)
                }

                is RewritePremiseContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs)
                    val rewrite = "val $rewriteRhs = $rewriteLhs"
                    Pair(null, rewrite)
                }

                is BooleanPremiseContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val (op, rewriteRhs) = when (premise.rhs) {
                        is FunconExpressionContext -> {
                            val type = Type(premise.rhs)
                            val rewriteRhs = buildTypeRewrite(type)
                            "${complementStr(type.complement)}is" to rewriteRhs
                        }

                        else -> {
                            when (premise.op.text) {
                                "==" -> "=="
                                "=/=" -> "!="
                                else -> throw DetailedException("Unexpected operator type: ${premise.op.text}")
                            } to buildRewrite(ruleDef, premise.rhs)
                        }
                    }
                    val condition = "$rewriteLhs $op $rewriteRhs"
                    Pair(condition, null)
                }

                is TypePremiseContext -> {
                    val value = buildRewrite(ruleDef, premise.value)
                    val type = Type(premise.type)
                    val typeStr = buildTypeRewrite(type)
                    val condition = "$value ${complementStr(type.complement)}is $typeStr"
                    Pair(condition, null)
                }

                else -> throw DetailedException("Unexpected premise type: ${premise::class.simpleName}")
            }
        }

        val conditions = result.mapNotNull { it.first }.joinToString(" && ")
        val rewrites = result.mapNotNull { it.second }.joinToString("\n")

        return Pair(conditions, rewrites)
    }

    override fun makeContent(): String {
        val pairs = rules.map { rule ->
            val premises = rule.premises()?.premise()?.toList() ?: emptyList()
            val conclusion = rule.conclusion

            val (ruleDef, conclusionConditions, conclusionRewrite) = processConclusion(conclusion)

            val (premiseConditions, premiseRewrite) = processPremises(premises, ruleDef)

            val finalConditions =
                listOf(premiseConditions, conclusionConditions).filter { it.isNotEmpty() }.joinToString(" && ")

            val finalRewrite = listOf(premiseRewrite, conclusionRewrite).filter { it.isNotEmpty() }.joinToString("\n")

            Pair(finalConditions, finalRewrite)
        }

        if (pairs.any { it.first.isEmpty() }) throw EmptyConditionException(name)

        val content = "return " + makeIfStatement(*pairs.toTypedArray(), elseBranch = "fail()")

        return makeExecuteFunction(content, returnStr)
    }

}