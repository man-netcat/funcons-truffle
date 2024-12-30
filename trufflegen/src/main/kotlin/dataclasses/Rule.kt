package main.dataclasses

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException

class Rule(premises: List<PremiseExprContext>, conclusion: PremiseExprContext) {
    val conditions: String
    val rewrite: String

    private fun complementStr(bool: Boolean): String = if (bool) "!" else ""

    private fun argsConditions(funconExpr: FunconExpressionContext): String {
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
                        val typeStr = rewriteType(argType)
                        "$paramStr ${complementStr(argType.isComplement)}is $typeStr"
                    }

                    is NumberContext -> "$paramStr == ${argValue.text}"
                    is TupleExpressionContext -> "${paramStr}.isEmpty()"
                    is VariableContext, is SuffixExpressionContext -> null
                    else -> throw IllegalArgumentException("Unexpected arg type: ${argValue::class.simpleName}, ${argValue.text}")
                }

                val typeCondition = if (argType != null) {
                    val argType = Type(argType)
                    val typeStr = rewriteType(argType)
                    "$paramStr ${complementStr(argType.isComplement)}is $typeStr"
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

    private fun processPremise(ruleDef: ExprContext, premise: PremiseExprContext): Pair<String?, String?> {
        val (lhs, rhs) = extractLhsRhs(premise)
        val rewriteLhs = rewrite(ruleDef, lhs)

        return when (premise) {
            is RewritePremiseContext -> {
                val rewriteRhs = rewrite(ruleDef, rhs)
                val rewrite = "val $rewriteRhs = $rewriteLhs"
                Pair(null, rewrite)
            }

            is TransitionPremiseContext -> {
                val rewriteRhs = rewrite(ruleDef, rhs)
                val condition = "$rewriteLhs is $COMPUTATION"
                val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                Pair(condition, rewrite)
            }

            is TransitionPremiseWithControlEntityContext -> {
                val rewriteRhs = rewrite(ruleDef, rhs)
                val condition = "$rewriteLhs is $COMPUTATION"
                val entityCondition = processPremiseEntity(ruleDef, premise)
                println("entityCondition: $entityCondition")
                val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                Pair(condition, rewrite)
            }

            is TransitionPremiseWithMutableEntityContext -> {
                val rewriteRhs = rewrite(ruleDef, rhs)
                val condition = "$rewriteLhs is $COMPUTATION"
                val entityCondition = processPremiseEntity(ruleDef, premise)
                println("entityCondition: $entityCondition")
                val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                Pair(condition, rewrite)
            }

            is TransitionPremiseWithContextualEntityContext -> {
                val rewriteRhs = rewrite(ruleDef, rhs)
                val condition = "$rewriteLhs is $COMPUTATION"
                val entityCondition = processPremiseEntity(ruleDef, premise)
                println("entityCondition: $entityCondition")
                val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                Pair(condition, rewrite)
            }

            is BooleanPremiseContext -> {
                val (op, rewriteRhs) = if (premise.rhs is FunconExpressionContext) {
                    val type = Type(premise.rhs)
                    val rewriteRhs = rewriteType(type)
                    "${complementStr(type.isComplement)}is" to rewriteRhs
                } else when (premise.op.text) {
                    "==" -> "=="
                    "=/=" -> "!="
                    else -> throw DetailedException("Unexpected operator type: ${premise.op.text}")
                } to rewrite(ruleDef, premise.rhs)
                val condition = "$rewriteLhs $op $rewriteRhs"
                Pair(condition, null)
            }

            is TypePremiseContext -> {
                val type = Type(premise.type)
                val typeStr = rewriteType(type)
                val condition = "$rewriteLhs ${complementStr(type.isComplement)}is $typeStr"
                Pair(condition, null)
            }

            else -> Pair(null, null)
        }
    }

    private fun processConclusionEntities(ruleDef: ExprContext, conclusion: PremiseExprContext): String {
        fun helper(label: LabelContext, putFunc: (String, String) -> String): String {
            val entityRewrite = rewrite(ruleDef, label)
            return putFunc(label.name.text, entityRewrite)
        }

        return when (conclusion) {
            is TransitionPremiseWithControlEntityContext -> {
                val steps = conclusion.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                steps.joinToString("\n") { step ->
                    val labels = step.labels().label()
                    labels.joinToString("\n") { label -> helper(label, ::putGlobal) }
                }
            }

            is TransitionPremiseWithMutableEntityContext -> helper(conclusion.entityLhs, ::putGlobal)
            is TransitionPremiseWithContextualEntityContext -> helper(conclusion.context_, ::putInScope)
            else -> ""
        }
    }

    private fun processPremiseEntity(ruleDef: ExprContext, premise: PremiseExprContext): String {
        fun helper(label: LabelContext, getFunc: (String) -> String): String {
            val entityRewrite = rewrite(ruleDef, label)
            val entityStr = getFunc(label.name.text)
            return "$entityStr == $entityRewrite"
        }

        return when (premise) {
            is TransitionPremiseWithControlEntityContext -> {
                val steps = premise.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                steps.joinToString(" && ") { step ->
                    val labels = step.labels().label()
                    labels.joinToString(" && ") { label -> helper(label, ::getGlobal) }
                }
            }

            is TransitionPremiseWithMutableEntityContext -> helper(premise.entityLhs, ::getGlobal)
            is TransitionPremiseWithContextualEntityContext -> helper(premise.context_, ::getInScope)
            else -> ""
        }
    }

    private fun extractLhsRhs(premiseExpr: PremiseExprContext): Pair<ExprContext, ExprContext> {
        return when (premiseExpr) {
            // TODO: There has to be a better way to do this
            is RewritePremiseContext -> premiseExpr.lhs to premiseExpr.rhs
            is TransitionPremiseContext -> premiseExpr.lhs to premiseExpr.rhs
            is TransitionPremiseWithContextualEntityContext -> premiseExpr.lhs to premiseExpr.rhs
            is TransitionPremiseWithControlEntityContext -> premiseExpr.lhs to premiseExpr.rhs
            is TransitionPremiseWithMutableEntityContext -> premiseExpr.lhs to premiseExpr.rhs
            is BooleanPremiseContext -> premiseExpr.lhs to premiseExpr.rhs
            is TypePremiseContext -> premiseExpr.value to premiseExpr.type
            else -> throw DetailedException("Unexpected premise type: ${premiseExpr::class.simpleName}")
        }
    }

    init {
        val (ruleDef, toRewrite) = extractLhsRhs(conclusion)

        val premisePairs = premises.map { premise -> processPremise(ruleDef, premise) }

        val premiseConditions = premisePairs.mapNotNull { it.first }.joinToString(" && ")
        val premiseRewrite = premisePairs.mapNotNull { it.second }.joinToString("\n")

        val entityRewrite = processConclusionEntities(ruleDef, conclusion)

        val conclusionConditions = if (ruleDef is FunconExpressionContext) {
            argsConditions(ruleDef)
        } else ""

        val conclusionRewrite = rewrite(ruleDef, toRewrite)

        conditions = listOf(premiseConditions, conclusionConditions).filter { it.isNotEmpty() }.joinToString(" && ")

        rewrite = listOf(entityRewrite, premiseRewrite, conclusionRewrite).filter { it.isNotEmpty() }.joinToString("\n")
    }
}
