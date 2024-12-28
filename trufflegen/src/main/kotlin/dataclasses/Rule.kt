package main.dataclasses

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException

class Rule(premises: List<PremiseExprContext>, conclusion: PremiseExprContext) {
    private fun complementStr(bool: Boolean): String = if (bool) "!" else ""

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

    val conditions: String
    val rewrite: String

    init {
        val (ruleDef, toRewrite) = when (conclusion) {
            // TODO: There has to be a better way to do this
            is RewritePremiseContext -> conclusion.lhs to conclusion.rhs
            is TransitionPremiseContext -> conclusion.lhs to conclusion.rhs
            is TransitionPremiseWithContextualEntityContext -> conclusion.lhs to conclusion.rhs
            is TransitionPremiseWithControlEntityContext -> conclusion.lhs to conclusion.rhs
            is TransitionPremiseWithMutableEntityContext -> conclusion.lhs to conclusion.rhs
            else -> throw DetailedException("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }

        val premisePair = premises.map { premise ->
            when (premise) {
                is RewritePremiseContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs)
                    val rewrite = "val $rewriteRhs = $rewriteLhs"
                    Pair(null, rewrite)
                }

                is TransitionPremiseContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs)
                    val condition = "$rewriteLhs is $COMPUTATION"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                    Pair(condition, rewrite)
                }

                is TransitionPremiseWithControlEntityContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs)
                    val condition = "$rewriteLhs is $COMPUTATION"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"

                    Pair(condition, rewrite)
                }

                is TransitionPremiseWithMutableEntityContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs)
                    val condition = "$rewriteLhs is $COMPUTATION"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                    Pair(condition, rewrite)
                }

                is TransitionPremiseWithContextualEntityContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs)
                    val condition = "$rewriteLhs is $COMPUTATION"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                    Pair(condition, rewrite)
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

                else -> Pair(null, null)
            }
        }

        val premiseConditions = premisePair.mapNotNull { it.first }.joinToString(" && ")
        val premiseRewrite = premisePair.mapNotNull { it.second }.joinToString("\n")

        val conclusionEntityStr = when (conclusion) {
            is TransitionPremiseWithControlEntityContext -> {
                val steps = conclusion.steps().step()
                steps.joinToString("\n") { step ->
                    val labels = step.labels().label()
                    labels.joinToString("\n") { label ->
                        putGlobal(label.name.text, buildRewrite(ruleDef, label))
                    }
                }
            }

            is TransitionPremiseWithMutableEntityContext -> {
                val label = conclusion.entityLhs
                putGlobal(label.name.text, buildRewrite(ruleDef, label))
            }

            is TransitionPremiseWithContextualEntityContext -> {
                val label = conclusion.context_
                putInScope(label.name.text, buildRewrite(ruleDef, label))
            }

            else -> ""
        }

        val conclusionConditions = if (ruleDef is FunconExpressionContext) {
            argsConditions(ruleDef)
        } else ""

        val conclusionRewrite = buildString {
            if (conclusionEntityStr.isNotEmpty()) {
                append("$conclusionEntityStr\n")
            }
            append(buildRewrite(ruleDef, toRewrite))
        }

        conditions = listOf(premiseConditions, conclusionConditions).filter { it.isNotEmpty() }.joinToString(" && ")

        rewrite = listOf(premiseRewrite, conclusionRewrite).filter { it.isNotEmpty() }.joinToString("\n")

    }
}
