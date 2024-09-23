package trufflegen.main

import trufflegen.antlr.CBSParser.*
import kotlin.collections.contains
import kotlin.collections.mapNotNull
import kotlin.collections.set

class FunconObjectWithRules(
    override val context: FunconDefinitionContext,
    override val name: String,
    override val params: List<Param>,
    private val rules: List<RuleDefinitionContext>,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
) : FunconObject(
    context, name, params, returns, aliases
) {
    val rewritePremiseMap = mutableMapOf<String, String>()
    var nRewritePremises = 0


    private fun processConclusion(conclusion: PremiseContext): Triple<ExprContext, String, String> {
        fun processConclusionStep(step: StepContext, rewrite: String, ruleDef: ExprContext): String {
            return when (step) {
                is StepWithoutLabelsContext -> rewrite
                is StepWithLabelsContext -> {
                    val labelAssigns = step.labels().label().joinToString("\n") { label ->
                        println("label: ${label.name.text}, value: ${label.value?.text}")
                        val labelValue = if (label.value != null) buildRewrite(ruleDef, label.value, params) else "null"
                        "labelMap[\"${label.name.text}\"] = $labelValue"
                    }
                    "$labelAssigns\n$rewrite"
                }

                else -> throw Exception("Unexpected step type: ${step::class.simpleName}")
            }
        }

        return when (conclusion) {
            is RewritePremiseContext -> {
                val rewrite = buildRewrite(conclusion.lhs, conclusion.rhs, params)
                Triple(conclusion.lhs, "TODO", rewrite)
            }

            is StepPremiseContext -> {
                val stepExpr = conclusion.stepExpr()
                when (stepExpr) {
                    is StepExprWithMultipleStepsContext -> {
                        val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs, params)
                        val steps = stepExpr.steps().step()
                        val stepStr = steps.joinToString("\n") { step ->
                            processConclusionStep(step, rewrite, stepExpr.lhs)
                        }
                        Triple(stepExpr.lhs, "TODO", stepStr)
                    }

                    is StepExprWithSingleStepContext -> {
                        val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs, params)
                        val step = stepExpr.step()
                        val stepStr = processConclusionStep(step, rewrite, stepExpr.lhs)
                        Triple(stepExpr.lhs, "TODO", stepStr)
                    }

                    else -> throw Exception("Unexpected stepExpr type: ${stepExpr::class.simpleName}")
                }
            }

            is MutableEntityPremiseContext -> {
                val mutableExpr = conclusion.mutableExpr()
                val rewrite = buildRewrite(mutableExpr.lhs, mutableExpr.rhs, params)
                Triple(mutableExpr.lhs, "TODO", rewrite)
            }

            else -> throw Exception("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }
    }

    private fun processPremises(
        premises: List<PremiseContext>,
        ruleDef: ExprContext,
    ): Pair<String, String> {
        fun processPremiseStep(step: StepWithLabelsContext, ruleDef: ExprContext): String {
            return step.labels().label().joinToString("\n") { label ->
                val labelValue = if (label.value != null) buildRewrite(ruleDef, label.value, params) else "null"
                "labelMap[\"${label.name.text}\"] == $labelValue"
            }
        }

        val result = premises.map { premise ->
            when (premise) {
                is StepPremiseContext -> {
                    val stepExpr = premise.stepExpr()
                    when (stepExpr) {
                        is StepExprWithMultipleStepsContext -> {
                            val rewriteLhs = buildRewrite(ruleDef, stepExpr.lhs, params)
                            val rewriteRhs = buildRewrite(ruleDef, stepExpr.rhs, params)
                            val condition = "$rewriteLhs is Computation"
                            val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                            val steps = stepExpr.steps().step().filterIsInstance<StepWithLabelsContext>()
                            if (steps.isNotEmpty()) {
                                val labelConditions = steps.joinToString(" && ") { step ->
                                    processPremiseStep(step, stepExpr.lhs)
                                }
                                Pair("$condition && $labelConditions", rewrite)
                            } else {
                                Pair(condition, rewrite)
                            }
                        }

                        is StepExprWithSingleStepContext -> {
                            val rewriteLhs = buildRewrite(ruleDef, stepExpr.lhs, params)
                            val rewriteRhs = buildRewrite(ruleDef, stepExpr.rhs, params)
                            val condition = "$rewriteLhs is Computation"
                            val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                            val step = stepExpr.step()
                            if (step is StepWithLabelsContext) {
                                val labelCondition = processPremiseStep(step, stepExpr.lhs)
                                Pair("$condition && $labelCondition", rewrite)
                            } else {
                                Pair(condition, rewrite)
                            }
                        }

                        else -> throw Exception("Unexpected stepExpr type: ${stepExpr::class.simpleName}")
                    }
                }

                is RewritePremiseContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs, params)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs, params)
                    if (premise.rhs.text !in rewritePremiseMap) {
                        val rewrite = "val r$nRewritePremises = $rewriteLhs"
                        rewritePremiseMap[premise.rhs.text] = rewrite
                        nRewritePremises += 1
                    }
                    Pair(null, rewritePremiseMap[premise.rhs.text])
                }

                is BooleanPremiseContext -> {
                    val value = buildRewrite(ruleDef, premise.lhs, params)
                    val op = when (premise.op.text) {
                        "==" -> "=="
                        "=/=" -> "!="
                        else -> throw Exception("Unexpected operator type: ${premise.op.text}")
                    }
                    val condition = "$value $op ${premise.rhs.text}"
                    Pair(condition, null)
                }

                is TypePremiseContext -> {
                    val value = buildRewrite(ruleDef, premise.value, params)
                    val condition = when (val premiseType = premise.type) {
                        is ComplementExpressionContext -> "$value !is ${premiseType.operand.text}"
                        else -> "$value is ${premiseType.text}"
                    }
                    Pair(condition, null)
                }

                else -> throw Exception("Unexpected premise type: ${premise::class.simpleName}")
            }
        }

        val conditions = result.mapNotNull { it.first }.joinToString(" && ")
        val rewrites = result.mapNotNull { it.second }.joinToString("\n")

        return Pair(conditions, rewrites)
    }

    override fun makeContent(): String {
        val pairs = rules.map { rule ->
            val (ruleDef, conclusionConditions, conclusionRewrite) = processConclusion(rule.conclusion)
            val premises = rule.premises()?.premise()?.toList() ?: emptyList()

            premises.forEach { println("premise: ${it.text}") }
            println("conclusion: ${rule.conclusion.text}")

            val (premiseConditions, premiseRewrite) = processPremises(premises, ruleDef)

            val finalConditions =
                listOf(premiseConditions, conclusionConditions).filter { it.isNotEmpty() }.joinToString(" && ")

            val finalRewrite = listOf(premiseRewrite, conclusionRewrite).filter { it.isNotEmpty() }.joinToString("\n")

            Pair(finalConditions, finalRewrite)
        }

        val content = makeIfStatement(*pairs.toTypedArray(), elseBranch = "throw Exception(\"Illegal Argument\")")
        println(content)
        return content
    }

}