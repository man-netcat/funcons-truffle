package main.dataclasses

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException

class Rule(premises: List<PremiseContext>, conclusion: PremiseContext) {
    val ruleDef: Any
    val conclusionConditions: String
    val conclusionRewrite: String
    val premiseConditions: String
    val premiseRewrite: String
    val completeConditions: String
    val completeRewrite: String

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

    fun argsConditions(funconExpr: FunconExpressionContext): String {
        val obj = globalObjects[funconExpr.name.text]!!
        val args = extractArgs(funconExpr)
        val conditions = if (obj.params.size == 1 && args.isEmpty()) {
            if (obj.params[0].type.isVararg) "p0.isEmpty()" else "p0 == null"
        } else {
            val paramStrs = getParamStrs(funconExpr, isParam = true)
            paramStrs.flatMap { (argValue, argType, paramStr) ->
                val valueCondition = when (argValue) {
                    null -> null
                    is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext -> {
                        val argType = Type(argValue)
                        val typeStr = buildTypeRewrite(argType)
                        "$paramStr ${complementStr(argType.complement)}is $typeStr"
                    }

                    is NumberContext -> "$paramStr == ${argValue.text}"
                    is TupleExpressionContext -> "${paramStr}.isEmpty()"
                    is VariableContext, is SuffixExpressionContext -> null
                    else -> throw IllegalArgumentException("Unexpected arg type: ${argValue::class.simpleName}, ${argValue.text}")
                }

                val typeCondition = if (argType != null) {
                    val argType = Type(argType)
                    val typeStr = buildTypeRewrite(argType)
                    "$paramStr ${complementStr(argType.complement)}is $typeStr"
                } else null

                listOfNotNull(typeCondition, valueCondition)
            }.joinToString(" && ")
        }

        return if (conditions.isNotEmpty()) {
            conditions
        } else if (obj.varargParamIndex >= 0) {
            val (arrayArgs, nonArrayArgs) = partitionArrayArgs(args)
            if (arrayArgs.isNotEmpty() && nonArrayArgs.size == 1) {
                "p${obj.varargParamIndex}.isNotEmpty()"
            } else if (arrayArgs.isNotEmpty()) {
                "p${obj.varargParamIndex}.size >= ${nonArrayArgs.size}"
            } else {
                "p${obj.varargParamIndex}.size == ${nonArrayArgs.size}"
            }
        } else "true"
    }

    init {
        when (conclusion) {
            is RewritePremiseContext -> {
                ruleDef = conclusion.lhs
                conclusionRewrite = buildRewrite(ruleDef, conclusion.rhs)
                conclusionConditions = if (ruleDef is FunconExpressionContext) {
                    argsConditions(ruleDef)
                } else ""
            }

            is StepPremiseContext -> {
                val stepExpr = conclusion.stepExpr()
                ruleDef = stepExpr.lhs
                if (isInputOutputEntity(stepExpr)) Triple(
                    ruleDef, "TODO(\"Condition\")", "TODO(\"Content\")"
                )
                val entityStr = gatherLabels(stepExpr).joinToString("\n") { label ->
                    putGlobal(label.name.text) + " = " + if (label.value != null) {
                        buildRewrite(ruleDef, label.value)
                    } else "null"
                }
                val entityStrPrefix = if (entityStr.isNotEmpty()) {
                    "$entityStr\n"
                } else ""
                conclusionRewrite = entityStrPrefix + buildRewrite(ruleDef, stepExpr.rhs)
                conclusionConditions = if (ruleDef is FunconExpressionContext) {
                    argsConditions(ruleDef)
                } else ""
            }

            is MutableEntityPremiseContext -> {
                val mutableExpr = conclusion.mutableExpr()
                ruleDef = mutableExpr.lhs
                conclusionRewrite = buildRewrite(ruleDef, mutableExpr.rhs)
                conclusionConditions = if (ruleDef is FunconExpressionContext) {
                    argsConditions(ruleDef)
                } else ""
            }

            else -> throw DetailedException("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }

        val result = premises.map { premise ->
            when (premise) {
                is StepPremiseContext -> {
                    val stepExpr = premise.stepExpr()
                    val labelConditions = gatherLabels(stepExpr).joinToString(" && ") { label ->
                        val entity = getGlobal(label.name.text)
                        "$entity == " + if (label.value != null) {
                            buildRewrite(ruleDef, label.value)
                        } else "null"
                    }
                    val rewriteLhs = buildRewrite(ruleDef, stepExpr.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, stepExpr.rhs)
                    val condition = "$rewriteLhs is $COMPUTATION"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"

                    if (labelConditions.isNotBlank()) Pair("$condition && $labelConditions", rewrite)
                    else Pair(condition, rewrite)
                }

                is MutableEntityPremiseContext -> {
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

        premiseConditions = result.mapNotNull { it.first }.joinToString(" && ")
        premiseRewrite = result.mapNotNull { it.second }.joinToString("\n")

        completeConditions =
            listOf(premiseConditions, conclusionConditions).filter { it.isNotEmpty() }.joinToString(" && ")

        completeRewrite = listOf(premiseRewrite, conclusionRewrite).filter { it.isNotEmpty() }.joinToString("\n")

    }
}
