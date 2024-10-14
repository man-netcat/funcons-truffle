package trufflegen.main

import trufflegen.antlr.CBSParser.*
import trufflegen.main.RewriteVisitor.Companion.getParamIndex
import kotlin.collections.mapNotNull

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
    fun encapsulateSteps(
        stepExpr: StepExprContext,
        separator: String,
        processLabel: (LabelContext) -> String
    ): String {
        return stepExpr.steps()
            ?.step()
            ?.sortedBy { step -> step.sequenceNumber?.text?.toInt() }
            ?.mapNotNull { step ->
                step.labels()?.label()?.joinToString(separator) { label ->
                    processLabel(label)
                }
            }
            ?.filter { stepStr -> stepStr.isNotBlank() }
            ?.joinToString(separator) ?: ""
    }

    fun gatherLabels(
        stepExpr: StepExprContext,
        processLabel: (LabelContext) -> Pair<String, String>
    ): Map<String, String> {
        return stepExpr.steps()
            ?.step()
            ?.sortedBy { step -> step.sequenceNumber?.text?.toInt() }
            ?.flatMap { step ->
                step.labels()?.label()?.map { label ->
                    processLabel(label)
                } ?: emptyList()
            }
            ?.toMap() ?: emptyMap()
    }

    private fun processConclusion(conclusion: PremiseContext): Triple<ExprContext, String, String> {
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
                val rewrite = buildRewrite(stepExpr.lhs, stepExpr.rhs)
                val labelAssigns = encapsulateSteps(stepExpr, "\n") { label ->
                    val labelValue = if (label.value != null) {
                        buildRewrite(stepExpr.lhs, label.value)
                    } else "null"
                    "labelMap[\"${label.name.text}\"] = $labelValue"
                }
                val stepStr = if (labelAssigns.isNotEmpty()) "$labelAssigns\n$rewrite" else rewrite
                val conditions = if (stepExpr.lhs is FunconExpressionContext) {
                    argsConditions(stepExpr.lhs as FunconExpressionContext)
                } else ""
                Triple(stepExpr.lhs, conditions, stepStr)
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

    private fun processPremises(premises: List<PremiseContext>, ruleDef: ExprContext): Pair<String, String> {
        val result = premises.map { premise ->
            println("premise: ${premise.text}")
            when (premise) {
                is StepPremiseContext -> {
                    val stepExpr = premise.stepExpr()
                    val labelConditions = encapsulateSteps(stepExpr, " && ") { label ->
                        val labelValue = if (label.value != null) {
                            buildRewrite(stepExpr.lhs, label.value)
                        } else "null"
                        "labelMap[\"${label.name.text}\"] == $labelValue"
                    }
                    val rewriteLhs = buildRewrite(ruleDef, stepExpr.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, stepExpr.rhs)
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

    override fun makeContent(): String {
        val pairs = rules.map { rule ->
            val (ruleDef, conclusionConditions, conclusionRewrite) = processConclusion(rule.conclusion)
            val premises = rule.premises()?.premise()?.toList() ?: emptyList()

            val (premiseConditions, premiseRewrite) = processPremises(premises, ruleDef)

            val finalConditions =
                listOf(premiseConditions, conclusionConditions).filter { it.isNotEmpty() }.joinToString(" && ")

            val finalRewrite = listOf(premiseRewrite, conclusionRewrite).filter { it.isNotEmpty() }.joinToString("\n")

            Pair(finalConditions, finalRewrite)
        }

        val content =
            "return " + makeIfStatement(*pairs.toTypedArray(), elseBranch = "throw Exception(\"Illegal Argument\")")
        val returnStr = buildTypeRewrite(returns)
        return makeExecuteFunction(content, returnStr)
    }

}