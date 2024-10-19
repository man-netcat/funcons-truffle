package trufflegen.main

import trufflegen.antlr.CBSParser.*
import trufflegen.main.RewriteVisitor.Companion.getParamIndex
import kotlin.collections.mapNotNull
import kotlin.collections.orEmpty

class FunconObjectWithRules(
    name: String,
    ctx: FunconDefinitionContext,
    params: List<Param>,
    private val rules: List<RuleDefinitionContext>,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    metavariables: MutableMap<ExprContext, ExprContext>,
    builtin: Boolean
) : FunconObject(name, ctx, params, returns, aliases, metavariables, builtin) {
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
        conclusion: PremiseContext, entityMap: Map<String, String>
    ): Triple<ExprContext, String, String> {
        println("conclusion: ${conclusion.text}")
        fun argsConditions(def: FunconExpressionContext): String {
            fun rewriteArg(args: List<ExprContext>): String {
                // First extract the actual value from the argument, or the type if no value available.
                val processedArgs = args.map { arg ->
                    when (arg) {
                        is TypeExpressionContext -> arg.value ?: arg.type
                        else -> arg
                    }
                }
                return processedArgs.mapIndexed { index, arg ->
                    val (index, varargIndex) = getParamIndex(index, params, processedArgs)
                    val paramStr = if (varargIndex != null) "p$index[$varargIndex]" else "p$index"
                    when (arg) {
                        is FunconExpressionContext -> {
                            val rewrite = buildTypeRewrite(ReturnType(arg))
                            "$paramStr is $rewrite"
                        }

                        is NumberContext -> "$paramStr == ${arg.text}"
                        is VariableContext -> null
                        is VariableStepContext -> null
                        is SuffixExpressionContext -> null
                        is TupleExpressionContext -> "$paramStr == ${buildRewrite(def, arg)}"
                        is ListExpressionContext -> {
                            val rewrite = buildRewrite(def, arg)
                            if (rewrite == "emptyList()") "$paramStr.isEmpty()"
                            else "$paramStr == $rewrite"
                        }

                        else -> throw Exception("Unexpected arg type: ${arg::class.simpleName}")
                    }
                }.filterNotNull().joinToString(" && ")
            }

            return when (val args = def.args()) {
                is MultipleArgsContext -> rewriteArg(args.exprs().expr())
                is SingleArgsContext -> rewriteArg(listOf(args.expr()))
                else -> ""
            }
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
                    entityMap(label.name.text) + " = " + buildRewrite(stepExpr.lhs, label.value)
                }
                var rewriteStr = buildRewrite(stepExpr.lhs, stepExpr.rhs, entityMap)
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

            else -> throw Exception("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }
    }

    private fun processPremises(
        premises: List<PremiseContext>, ruleDef: ExprContext, entityMap: Map<String, String>
    ): Pair<String, String> {
        val result = premises.map { premise ->
            println("premise: ${premise.text}")
            when (premise) {
                is StepPremiseContext -> {
                    val stepExpr = premise.stepExpr()
                    val labelConditions = gatherLabels(stepExpr).joinToString(" && ") { label ->
                        entityMap(label.name.text) + " == " + (if (label.value != null) {
                            buildRewrite(ruleDef, label.value)
                        } else "null")
                    }
                    val rewriteLhs = buildRewrite(ruleDef, stepExpr.lhs, entityMap)
                    val rewriteRhs = buildRewrite(ruleDef, stepExpr.rhs, entityMap)
                    val condition = "$rewriteLhs is Computation"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"

                    if (labelConditions.isNotBlank()) {
                        Pair("$condition && $labelConditions", rewrite)
                    } else {
                        Pair(condition, rewrite)
                    }
                }

                is RewritePremiseContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs)
                    val rewrite = "val $rewriteRhs = $rewriteLhs"
                    Pair(null, rewrite)
                }

                is BooleanPremiseContext -> {
                    val value = buildRewrite(ruleDef, premise.lhs)
                    val op = when (premise.op.text) {
                        "==" -> "=="
                        "=/=" -> "!="
                        else -> throw Exception("Unexpected operator type: ${premise.op.text}")
                    }
                    val condition = "$value $op ${premise.rhs.text}"
                    Pair(condition, null)
                }

                is TypePremiseContext -> {
                    val value = buildRewrite(ruleDef, premise.value)
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

    private fun buildEntityMap(premises: List<PremiseContext>, conclusion: PremiseContext): Map<String, String> {
        return (premises + conclusion).flatMap { premise ->
            if (premise is StepPremiseContext) {
                val stepExpr = premise.stepExpr()
                if (stepExpr.context_?.name != null) {
                    if (stepExpr.context_.value != null) {
                        listOf(stepExpr.context_.value.text to stepExpr.context_.name.text)
                    } else emptyList()
                } else {
                    stepExpr.steps()?.step()?.sortedBy { step -> step.sequenceNumber?.text?.toInt() }?.flatMap { step ->
                            step.labels()?.label()?.filter { label -> label.value != null }
                                ?.map { label -> label.value.text to label.name.text }.orEmpty()
                        }.orEmpty()
                }
            } else if (premise is MutableEntityPremiseContext) {
                emptyList()
            } else emptyList()
        }.toMap()
    }

    override fun makeContent(): String {
        val pairs = rules.map { rule ->
            val premises = rule.premises()?.premise()?.toList() ?: emptyList()
            val conclusion = rule.conclusion

            val entityMap = buildEntityMap(premises, conclusion)

            val (ruleDef, conclusionConditions, conclusionRewrite) = processConclusion(conclusion, entityMap)

            val (premiseConditions, premiseRewrite) = processPremises(premises, ruleDef, entityMap)

            val finalConditions =
                listOf(premiseConditions, conclusionConditions).filter { it.isNotEmpty() }.joinToString(" && ")

            val finalRewrite = listOf(premiseRewrite, conclusionRewrite).filter { it.isNotEmpty() }.joinToString("\n")

            Pair(finalConditions, finalRewrite)
        }

        if (pairs.any { it.first.isEmpty() }) throw EmptyConditionException(name)

        val content =
            "return " + makeIfStatement(*pairs.toTypedArray(), elseBranch = "throw Exception(\"Illegal Argument\")")
        val returnStr = buildTypeRewrite(returns)
        return makeExecuteFunction(content, returnStr)
    }

}