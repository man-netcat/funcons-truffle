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
//        println("conclusion: ${conclusion.text}")

        val ruleDef: ExprContext
        val rewriteExpr: ExprContext

        when (conclusion) {
            is RewritePremiseContext -> {
                ruleDef = conclusion.lhs
                rewriteExpr = conclusion.rewritesTo
            }

            is StepPremiseContext -> {
                val stepExpr = conclusion.stepExpr()
                ruleDef = stepExpr.lhs
                rewriteExpr = stepExpr.rewritesTo
            }

            is MutableEntityPremiseContext -> {
                val mutableExpr = conclusion.mutableExpr()
                ruleDef = mutableExpr.lhs
                rewriteExpr = mutableExpr.rhs
            }

            else -> throw Exception("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }
//        println("Conclusion type: ${conclusion::class.simpleName}")
        val conclusionRewrite = buildRewrite(ruleDef, rewriteExpr, params)
//        println("Conclusion rewrite: $conclusionRewrite")

        val rewritePremiseMap = mutableMapOf<String, Pair<String, String>>()
        var nRewritePremises = 0
        val conditions = premises.map { premise ->
            when (premise) {
                is RewritePremiseContext -> {
                    val rewritePremise = buildRewrite(ruleDef, premise.lhs, params)
                    rewritePremiseMap[premise.rewritesTo.text] = Pair(rewritePremise, "r$nRewritePremises")
                    println("rewritePremise: ${premise.rewritesTo.text} -> r$nRewritePremises = $rewritePremise")
                    nRewritePremises += 1
                }

                is StepPremiseContext -> {}
                is MutableEntityPremiseContext -> {}
                is BooleanPremiseContext -> {
//                    println("Boolean Premise Expression")
                    val value = buildRewrite(ruleDef, premise.lhs, params)
                    val op = when (premise.op.text) {
                        "==" -> "=="
                        "=/=" -> "!="
                        else -> throw Exception("Unexpected operator type: ${premise.op.text}")
                    }
                    "$value $op ${premise.rhs.text}"
                }

                is TypePremiseContext -> {
//                    println("Type Premise Expression")
                    val value = buildRewrite(ruleDef, premise.value, params)
                    when (val premiseType = premise.type) {
                        is ComplementExpressionContext -> {
                            "$value !is ${premiseType.operand.text}"
                        }

                        else -> "$value is ${premiseType.text}"
                    }
                }

                else -> throw Exception("Unexpected premise type: ${premise::class.simpleName}")
            }
        }
        println("Conditions:")
        conditions.forEach { condition -> println(condition) }
        return ""
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