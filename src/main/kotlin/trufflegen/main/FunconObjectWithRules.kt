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
    val rewritePremiseMap = mutableMapOf<String, Pair<String, String>>()
    var nRewritePremises = 0

    private fun processConclusionStep(step: StepContext, rewrite: String, ruleDef: ExprContext): String {
        return when (step) {
            is StepWithoutLabelsContext -> rewrite
            is StepWithLabelsContext -> {
                val labelAssigns = step.labels().label().joinToString("/n") { label ->
                    println("label: ${label.name.text}, value: ${label.value?.text}")
                    val labelValue = if (label.value != null) buildRewrite(ruleDef, label.value, params) else "null"
                    "labelMap[\"${label.name.text}\"] = $labelValue"
                }
                "$labelAssigns\n$rewrite"
            }

            else -> throw Exception("Unexpected step type: ${step::class.simpleName}")
        }
    }

    private fun processConclusionStepPremise(conclusion: StepPremiseContext): Triple<ExprContext, ExprContext, String> {
        val stepExpr = conclusion.stepExpr()
        return when (stepExpr) {
            is StepExprWithMultipleStepsContext -> {
                val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs, params)
                val stepStr = stepExpr.steps().step().joinToString("\n") { step ->
                    processConclusionStep(step, rewrite, stepExpr.lhs)
                }
                Triple(stepExpr.lhs, stepExpr.rhs, stepStr)
            }

            is StepExprWithSingleStepContext -> {
                val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs, params)
                val stepStr = processConclusionStep(stepExpr.step(), rewrite, stepExpr.lhs)
                Triple(stepExpr.lhs, stepExpr.rhs, stepStr)
            }

            else -> throw Exception("Unexpected stepExpr type: ${stepExpr::class.simpleName}")
        }
    }

    private fun processConclusion(conclusion: PremiseContext): Triple<ExprContext, ExprContext, String> {
        return when (conclusion) {
            is RewritePremiseContext -> Triple(
                conclusion.lhs, conclusion.rhs, buildRewrite(conclusion.lhs, conclusion.rhs, params)
            )

            is StepPremiseContext -> processConclusionStepPremise(conclusion)

            is MutableEntityPremiseContext -> {
                val mutableExpr = conclusion.mutableExpr()
                Triple(mutableExpr.lhs, mutableExpr.rhs, buildRewrite(mutableExpr.lhs, mutableExpr.rhs, params))
            }

            else -> throw Exception("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }
    }

    private fun processRewrite(premise: RewritePremiseContext, ruleDef: ExprContext): String {
        val rewritePremise = buildRewrite(ruleDef, premise.lhs, params)
        println("rewritePremise: ${premise.rhs.text} -> r$nRewritePremises = $rewritePremise")
        if (premise.rhs.text !in rewritePremiseMap) {
            rewritePremiseMap[premise.rhs.text] = Pair(rewritePremise, "r$nRewritePremises")
            nRewritePremises += 1
        }
        return "" // TODO
    }

    private fun processStep(step: StepContext, rewrite: String, premiseLhs: ExprContext): String {
        return when (step) {
            is StepWithoutLabelsContext -> rewrite
            is StepWithLabelsContext -> {
                val labelAssigns = step.labels().label().joinToString("/n") { label ->
                    println("label: ${label.name.text}, value: ${label.value?.text}")
                    val labelValue = if (label.value != null) buildRewrite(premiseLhs, label.value, params) else "null"
                    "labelMap[\"${label.name.text}\"] = $labelValue"
                }
                "$labelAssigns\n$rewrite"
            }

            else -> throw Exception("Unexpected step type: ${step::class.simpleName}")
        }
    }

    private fun processStepPremise(premise: StepPremiseContext, ruleDef: ExprContext): String {
        val stepExpr = premise.stepExpr()
        return when (stepExpr) {
            is StepExprWithMultipleStepsContext -> {
                val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs, params)
                stepExpr.steps().step().joinToString("\n") { step ->
                    processStep(step, rewrite, stepExpr.lhs)
                }
            }

            is StepExprWithSingleStepContext -> {
                val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs, params)
                processStep(stepExpr.step(), rewrite, stepExpr.lhs)
            }

            else -> throw Exception("Unexpected stepExpr type: ${stepExpr::class.simpleName}")
        }
    }

    private fun processBooleanPremise(premise: BooleanPremiseContext, ruleDef: ExprContext): String {
        val value = buildRewrite(ruleDef, premise.lhs, params)
        val op = when (premise.op.text) {
            "==" -> "=="
            "=/=" -> "!="
            else -> throw Exception("Unexpected operator type: ${premise.op.text}")
        }
        return "$value $op ${premise.rhs.text}"
    }

    private fun processTypePremise(premise: TypePremiseContext, ruleDef: ExprContext): String {
        val value = buildRewrite(ruleDef, premise.value, params)
        return when (val premiseType = premise.type) {
            is ComplementExpressionContext -> "$value !is ${premiseType.operand.text}"
            else -> "$value is ${premiseType.text}"
        }
    }

    private fun processConditions(premises: List<PremiseContext>, ruleDef: ExprContext): String {
        return premises.joinToString(" && ") { premise ->
            when (premise) {
                is RewritePremiseContext -> processRewrite(premise, ruleDef)
                is StepPremiseContext -> processStepPremise(premise, ruleDef)
                is BooleanPremiseContext -> processBooleanPremise(premise, ruleDef)
                is TypePremiseContext -> processTypePremise(premise, ruleDef)
                else -> throw Exception("Unexpected premise type: ${premise::class.simpleName}")
            }
        }
    }


    override fun makeContent(): String {
        val content = rules.joinToString("\n") { rule ->
            val (ruleDef, rewriteExpr, conclusionRewrite) = processConclusion(rule.conclusion)
            val premises = rule.premises()?.premise()?.toList() ?: emptyList()
            val conditions = processConditions(premises, ruleDef)
            if (!conditions.isEmpty()) makeIfStatement(conditions, conclusionRewrite)
            else conclusionRewrite
        }
        println(content)
        return content
    }
}