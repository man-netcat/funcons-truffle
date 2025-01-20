package main.dataclasses

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException

class Rule(premises: List<PremiseExprContext>, conclusion: PremiseExprContext, returns: Type) {
    val conditions: MutableList<String> = mutableListOf()
    val assignments: MutableList<String> = mutableListOf()
    val rewrite: String
    var intermediateCounter = 0

    val conditionStr: String
        get() = conditions.joinToString(" && ")

    val bodyStr: String
        get() = (assignments + rewrite).joinToString("\n")

    private fun newVar() = "i${intermediateCounter++}"

    private fun makeTypeCondition(paramStr: String, typeExpr: ExprContext): String {
        val argType = Type(typeExpr)
        val typeStr = rewriteType(argType)
        val complementStr = if (argType.isComplement) "!" else ""
        return "$paramStr ${complementStr}is $typeStr"
    }

    private fun argsConditions(funconExpr: FunconExpressionContext, rewriteData: List<RewriteData>) {
        val obj = globalObjects[funconExpr.name.text]!!
        val args = extractArgs(funconExpr)
        if (obj.params.size == 1 && args.isEmpty()) {
            conditions.add(if (obj.params[0].type.isVararg) "p0.isEmpty()" else "p0 == null")
        } else {
            val paramStrs = getParamStrs(funconExpr, isParam = true)
            (paramStrs + rewriteData).forEach { (argValue, argType, paramStr) ->
                val valueCondition = when (argValue) {
                    null -> null
                    is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext -> makeTypeCondition(
                        paramStr, argValue
                    )

                    is NumberContext -> "$paramStr == ${argValue.text}"
                    is TupleExpressionContext -> "${paramStr}.isEmpty()"
                    is VariableContext -> if (argValue.text == "_") "$paramStr != null" else null
                    is SuffixExpressionContext -> null
                    else -> throw IllegalArgumentException("Unexpected arg type: ${argValue::class.simpleName}, ${argValue.text}")
                }

                if (valueCondition != null) conditions.add(valueCondition)

                if (argType != null) {
                    val typeCondition = makeTypeCondition(paramStr, argType)
                    conditions.add(typeCondition)
                }
            }
        }

        if (conditions.isEmpty()) {
            val condition = if (obj.varargParamIndex >= 0) {
                val (arrayArgs, nonArrayArgs) = partitionArrayArgs(args)
                if (arrayArgs.isNotEmpty() && nonArrayArgs.size == 1) {
                    "p${obj.varargParamIndex}.isNotEmpty()"
                } else if (arrayArgs.isNotEmpty()) {
                    "p${obj.varargParamIndex}.size >= ${nonArrayArgs.size}"
                } else {
                    "p${obj.varargParamIndex}.size == ${nonArrayArgs.size}"
                }
            } else "true"

            conditions.add(condition)
        }
    }

    private fun processIntermediates(
        ruleDef: ExprContext, premise: PremiseExprContext, rewriteData: List<RewriteData>
    ): List<RewriteData> {
        var (lhs, rhs) = extractLhsRhs(premise)

        return when (premise) {
            is RewritePremiseContext -> {
                // TODO: Verify
                if (lhs is VariableContext && rhs is VariableContext) {
                    // Not sure if this could happen, but I guess this makes sure there's a variable to assign to
                    val lhsString = newVar()
                    val rhsString = newVar()
                    listOf(RewriteData(rhs, null, lhsString), RewriteData(lhs, null, rhsString))
                } else if (lhs is VariableContext) {
                    val string = rewrite(ruleDef, lhs, rewriteData)
                    getParamStrs(rhs, prefix = string)
                } else if (rhs is VariableContext) {
                    val string = newVar()
                    listOf(RewriteData(rhs, null, string))
                } else if (rhs is NestedExpressionContext) {
                    val nestedExpr = rhs.expr()
                    if (nestedExpr !is TypeExpressionContext) {
                        throw DetailedException("Unexpected expression of type ${nestedExpr::class.simpleName}, ${nestedExpr.text}")
                    }
                    val string = newVar()
                    listOf(RewriteData(nestedExpr.value, nestedExpr.type, string))
                } else if (rhs is TupleExpressionContext) {
                    val string = newVar()
                    listOf(RewriteData(rhs, null, string))
                } else throw DetailedException("Unexpected expression of type ${rhs::class.simpleName}, ${rhs.text}")
            }

            is TransitionPremiseContext, is TransitionPremiseWithControlEntityContext, is TransitionPremiseWithContextualEntityContext -> {
                val rewriteRhs = newVar()
                listOf(RewriteData(rhs, null, rewriteRhs))
            }

            is TransitionPremiseWithMutableEntityContext -> {
                val rewriteRhs = newVar()
                val entityRhs = newVar()
                listOf(RewriteData(rhs, null, rewriteRhs), RewriteData(premise.entityRhs.value, null, entityRhs))
            }

            else -> listOf(RewriteData(null, null, ""))
        }
    }

    private fun processPremises(
        ruleDef: ExprContext,
        premise: PremiseExprContext,
        rewriteData: List<RewriteData>,
    ) {
        val (lhs, rhs) = extractLhsRhs(premise)

        when (premise) {
            // TODO: Verify
            is RewritePremiseContext -> {
                // TODO: Fix
                val (rewriteLhs, rewriteRhs) = if (lhs is VariableContext) {
                    rewrite(ruleDef, rhs, rewriteData) to rewrite(ruleDef, lhs, rewriteData)
                } else {
                    rewrite(ruleDef, lhs, rewriteData) to rewrite(ruleDef, rhs, rewriteData)
                }

                val rewrite = "val $rewriteRhs = $rewriteLhs"
                assignments.add(rewrite)
            }

            is TransitionPremiseContext, is TransitionPremiseWithControlEntityContext, is TransitionPremiseWithContextualEntityContext, is TransitionPremiseWithMutableEntityContext -> {
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)
                val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                assignments.add(rewrite)

                if (lhs is VariableContext && rhs is VariableContext && rhs.text == lhs.text + "\'") {
                    // Base case, X usually rewrites to X'
                    val condition = "$rewriteLhs is $COMPUTATION"
                    conditions.add(condition)
                } else if (rhs is VariableContext && rhs.text == "_") {
                    // If executes to any non-null value
                    val condition = "$rewriteLhs is $COMPUTATION && $rewriteRhs != null"
                    conditions.add(condition)
                    assignments.add(rewrite)
                } else {
                    // some weird edge case
                    // TODO: FIX
                }
                if (premise is TransitionPremiseWithMutableEntityContext) {
                    val rewriteEntityLhs = rewrite(ruleDef, premise.entityLhs.value, rewriteData)
                    val rewriteEntityRhs = rewrite(ruleDef, premise.entityRhs.value, rewriteData)
                    val rewrite = "val $rewriteEntityRhs = $rewriteEntityLhs.execute(frame)"
                    assignments.add(rewrite)
                }
            }

            is BooleanPremiseContext -> {
                val condition = if (premise.rhs is FunconExpressionContext) {
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    makeTypeCondition(rewriteLhs, premise.rhs)
                } else {
                    val rewriteLhs = rewrite(ruleDef, premise.lhs, rewriteData)
                    val rewriteRhs = rewrite(ruleDef, premise.rhs, rewriteData)
                    val op = when (premise.op.text) {
                        "==" -> "=="
                        "=/=" -> "!="
                        else -> throw DetailedException("Unexpected operator type: ${premise.op.text}")
                    }
                    "$rewriteLhs $op $rewriteRhs"
                }
                conditions.add(condition)
            }

            is TypePremiseContext -> {
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val condition = makeTypeCondition(rewriteLhs, premise.type)
                conditions.add(condition)
            }
        }
    }

    private fun processConclusion(
        ruleDef: ExprContext, conclusion: PremiseExprContext, rewriteData: List<RewriteData>
    ) {
        when (conclusion) {
            is TransitionPremiseWithControlEntityContext -> {
                val steps = conclusion.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                steps.forEach { step ->
                    step.labels().label().forEach { label ->
                        val valueStr = if (label.value != null) {
                            rewrite(ruleDef, label.value, rewriteData)
                        } else "null"
                        val assignment = putGlobal(label.name.text, valueStr)
                        assignments.add(assignment)
                    }
                }
            }

            is TransitionPremiseWithContextualEntityContext -> {
                val label = conclusion.context_
                val valueStr = if (label.value != null) {
                    rewrite(ruleDef, label.value, rewriteData)
                } else "null"
                val assignment = putInScope(label.name.text, valueStr)
                assignments.add(assignment)
            }

            is TransitionPremiseWithMutableEntityContext -> {
                val label = conclusion.entityRhs
                val valueStr = if (label.value != null) {
                    rewrite(ruleDef, label.value, rewriteData)
                } else "null"
                val assignment = putGlobal(label.name.text, valueStr)
                assignments.add(assignment)
            }
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

    fun processEntities(premiseExpr: PremiseExprContext): List<RewriteData> {
        val labels = when (premiseExpr) {
            is TransitionPremiseWithControlEntityContext -> {
                val steps = premiseExpr.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                steps.flatMap { step -> step.labels().label() }
            }

            is TransitionPremiseWithMutableEntityContext -> listOf(premiseExpr.entityLhs)
            is TransitionPremiseWithContextualEntityContext -> listOf(premiseExpr.context_)
            else -> listOf()
        }

        if (labels.isEmpty()) return emptyList()

        val getFunc = when (premiseExpr) {
            is TransitionPremiseWithControlEntityContext, is TransitionPremiseWithMutableEntityContext -> ::getGlobal
            is TransitionPremiseWithContextualEntityContext -> ::getInScope
            else -> throw DetailedException("Unexpected premise type: ${premiseExpr::class.simpleName}")
        }

        return labels.flatMap { label ->
            val getStr = getFunc(label.name.text)
            getParamStrs(label, prefix = getStr)
        }
    }

    init {
        val (ruleDef, toRewrite) = extractLhsRhs(conclusion)

        val entityData = (premises + conclusion).flatMap { premise -> processEntities(premise) }

        val rewriteData = entityData.toMutableList()

        premises.forEach { premise ->
            rewriteData.addAll(processIntermediates(ruleDef, premise, rewriteData))
        }

        premises.forEach { premise ->
            processPremises(ruleDef, premise, rewriteData)
        }

        if (ruleDef is FunconExpressionContext) argsConditions(ruleDef, rewriteData)

        processConclusion(ruleDef, conclusion, rewriteData)

        rewrite = if (toRewrite is TupleExpressionContext && toRewrite.exprs() == null) {
            if (returns.isNullable) "null" else "emptyArray()"
        } else rewrite(ruleDef, toRewrite, rewriteData)
    }
}
