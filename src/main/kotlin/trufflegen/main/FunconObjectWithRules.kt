package trufflegen.main

import trufflegen.antlr.CBSParser.*

class FunconObjectWithRules(
    override val context: FunconDefinitionContext,
    override val name: String,
    override val params: List<Param>,
    private val rules: List<RuleDefinitionContext>,
    private val returns: ReturnType,
    private val aliases: List<AliasDefinitionContext>
) : FunconObject(
    context, name, params, returns, aliases
) {
    private fun buildTransition(rule: TransitionRuleContext): String {
        val premises = rule.premises().premise().toList()
        val conclusion = rule.conclusion
//        println("params: ${params.joinToString(", ", "(", ")") { param -> param.valueExpr?.text ?: "None" }}")
//        println("conclusion: ${conclusion.text}")
        val rewritten = when (conclusion) {
            is RewritePremiseContext -> {
//                println("rewrite conclusion: ${conclusion.text}")
                buildRewrite(conclusion.lhs, conclusion.rewritesTo, params)
            }

            is StepPremiseContext -> {
                val stepExpr = conclusion.stepExpr()
                buildRewrite(stepExpr.lhs, stepExpr.rewritesTo, params)

            }

            is MutableEntityPremiseContext -> {
                ""
            }

            else -> throw Exception("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }

        premises.forEach { premise ->
            when (premise) {
                is RewritePremiseContext -> {}
                is StepPremiseContext -> {}
                is MutableEntityPremiseContext -> {}
                is BooleanPremiseContext -> {}
                is TypePremiseContext -> {}
            }
        }
        return rewritten
    }

    override fun makeContent(): String {
        return rules.joinToString { rule ->
            when (rule) {
                is TransitionRuleContext -> buildTransition(rule)

                is RewriteRuleContext -> when (val premise = rule.premise()) {
                    is RewritePremiseContext -> {
//                        println("rewrite premise: ${premise.text}")
                        buildRewrite(premise.lhs, premise.rewritesTo, params)
                    }

                    is StepPremiseContext -> {
                        ""
                    }

                    is MutableEntityPremiseContext -> {
                        ""
                    }

                    else -> throw Exception("Unexpected premise type: ${premise::class.simpleName}")
                }

                else -> throw Exception("Unexpected rule type: ${rule::class.simpleName}")
            }
        }
    }
}