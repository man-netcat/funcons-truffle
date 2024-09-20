package trufflegen.main

import trufflegen.antlr.CBSParser.*
import kotlin.collections.contains
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
    override fun makeContent(): String {
        val content = rules.joinToString("\n") { rule ->
            val rewritePremiseMap = mutableMapOf<String, Pair<String, String>>()

            var nRewritePremises = 0

            val premises = rule.premises()?.premise()?.toList()
            val conclusion = rule.conclusion

            val ruleDef: ExprContext
            val rewriteExpr: ExprContext

            val conclusionRewrite = when (conclusion) {
                is RewritePremiseContext -> {
                    ruleDef = conclusion.lhs
                    buildRewrite(conclusion.lhs, conclusion.rhs, params)
                }

                is StepPremiseContext -> {
                    val stepExpr = conclusion.stepExpr()
                    if (stepExpr is StepExprWithMultipleStepsContext) {
                        ruleDef = stepExpr.lhs
                        val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs, params)
                        stepExpr.steps().step().joinToString("\n") { step ->
                            if (step is StepWithoutLabelsContext) rewrite
                            if (step !is StepWithLabelsContext) throw Exception("Unexpected step type: ${step::class.simpleName}")

                            val labelAssigns = step.labels().label().joinToString("/n") { label ->
                                println("label: ${label.name.text}, value: ${label.value?.text}")
                                val labelValue = if (label.value != null) buildRewrite(
                                    stepExpr.lhs, label.value, params
                                ) else "null"
                                "labelMap[${label.name.text}] = $labelValue"
                            }

                            "$labelAssigns\n$rewrite"
                        }
                    } else if (stepExpr is StepExprWithSingleStepContext) {
                        ruleDef = stepExpr.lhs
                        val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs, params)
                        val step = stepExpr.step()
                        if (step is StepWithoutLabelsContext) rewrite
                        else if (step !is StepWithLabelsContext) throw Exception("Unexpected step type: ${step::class.simpleName}")
                        else {
                            val labelAssigns = step.labels().label().joinToString("/n") { label ->
                                println("label: ${label.name.text}, value: ${label.value?.text}")
                                val labelValue = if (label.value != null) buildRewrite(
                                    stepExpr.lhs, label.value, params
                                ) else "null"
                                "labelMap[${label.name.text}] = $labelValue"
                            }

                            "$labelAssigns\n$rewrite"
                        }
                    } else throw Exception("Unexpected stepExpr type: ${stepExpr::class.simpleName}")
                }

                is MutableEntityPremiseContext -> {
                    val mutableExpr = conclusion.mutableExpr()
                    ruleDef = mutableExpr.lhs
                    rewriteExpr = mutableExpr.rhs
                    buildRewrite(mutableExpr.lhs, mutableExpr.rhs, params)
                }

                else -> {
                    throw Exception("Unexpected conclusion type: ${conclusion::class.simpleName}")
                }
            }

            val conditions = premises?.joinToString(" && ") { premise ->
                when (premise) {
                    is RewritePremiseContext -> {
                        val rewritePremise = buildRewrite(ruleDef, premise.lhs, params)
                        println("rewritePremise: ${premise.rhs.text} -> r$nRewritePremises = $rewritePremise")
                        if (premise.rhs.text !in rewritePremiseMap) {
                            rewritePremiseMap[premise.rhs.text] = Pair(rewritePremise, "r$nRewritePremises")
                            nRewritePremises += 1
                        }
                        ""
                    }

                    is StepPremiseContext -> {
                        println("stepPremise: ${premise.text}")
                        ""
                    }

                    is BooleanPremiseContext -> {
                        val value = buildRewrite(ruleDef, premise.lhs, params)
                        val op = when (premise.op.text) {
                            "==" -> "=="
                            "=/=" -> "!="
                            else -> throw Exception("Unexpected operator type: ${premise.op.text}")
                        }
                        "$value $op ${premise.rhs.text}"
                    }

                    is TypePremiseContext -> {
                        val value = buildRewrite(ruleDef, premise.value, params)
                        when (val premiseType = premise.type) {
                            is ComplementExpressionContext -> "$value !is ${premiseType.operand.text}"
                            else -> "$value is ${premiseType.text}"
                        }
                    }

                    else -> throw Exception("Unexpected premise type: ${premise::class.simpleName}")
                }
            }
            "$conditions\n$conclusionRewrite"
        }
        println(content)
        return content
    }
}