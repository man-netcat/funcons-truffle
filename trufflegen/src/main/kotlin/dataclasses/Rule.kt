package main.dataclasses

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException
import main.objects.EntityObject

class Rule(premises: List<PremiseExprContext>, conclusion: PremiseExprContext, returns: Type) {
    val conditions: MutableList<String> = mutableListOf()
    val emptyConditions: MutableList<String> = mutableListOf()
    private val assignments: MutableList<String> = mutableListOf()
    private val rewriteStr: String
    private var intermediateCounter = 0

    val bodyStr: String
        get() = (assignments + rewriteStr).joinToString("\n")

    private fun newVar() = "i${intermediateCounter++}"

    private fun makeTypeCondition(paramStr: String, typeExpr: ExprContext): String {
        val argType = Type(typeExpr)
        val typeStr = argType.rewrite()
        val complementStr = if (argType.isComplement) "!" else ""
        return "$paramStr ${complementStr}is $typeStr"
    }

    private fun argsConditions(funconExpr: FunconExpressionContext, rewriteData: List<RewriteData>) {
        val obj = getObject(funconExpr)
        val args = extractArgs(funconExpr)
        if (obj.params.size == 1 && args.isEmpty()) {
            if (obj.params[0].type.isVararg) {
                emptyConditions.add("p0.isEmpty()")
            } else {
                conditions.add("p0 == null")
            }
        } else {
            val paramStrs = getParamStrs(funconExpr)
            (paramStrs + rewriteData).forEach { data ->
                val (argValue, argType, paramStr) = data

                if (argType == null && argValue == null) {
                    emptyConditions.add("${paramStr}.isEmpty()")
                }

                when (argType) {
                    is SuffixExpressionContext -> if (argType.op.text == "+") {
                        // If it's an expression of the type "X+" it cannot be empty.
                        val typeCondition = "${paramStr}.isNotEmpty()"
                        conditions.add(typeCondition)
                    }

                    is FunconExpressionContext,
                    is ListExpressionContext,
                    is SetExpressionContext,
                    is ComplementExpressionContext -> {
                        val typeCondition = makeTypeCondition(paramStr, argType)
                        conditions.add(typeCondition)
                    }

                    is VariableContext -> {}
                }

                when (argValue) {
                    is NumberContext -> conditions.add("$paramStr == ${argValue.text}")
                    is TupleExpressionContext -> emptyConditions.add("${paramStr}.isEmpty()")
                    is VariableContext -> if (argValue.text == "_") conditions.add("$paramStr != null")
                    is SuffixExpressionContext -> {}
                }
            }
        }

        if ((emptyConditions + conditions).isEmpty()) {
            if (obj.hasVararg) {
                val (arrayArgs, nonArrayArgs) = partitionArrayArgs(args)
                if (arrayArgs.isNotEmpty() && nonArrayArgs.size == 1) {
                    conditions.add("p${obj.varargParamIndex}.isNotEmpty()")
                } else if (arrayArgs.isNotEmpty()) {
                    conditions.add("p${obj.varargParamIndex}.size >= ${nonArrayArgs.size}")
                } else {
                    conditions.add("p${obj.varargParamIndex}.size == ${nonArrayArgs.size}")
                }
            } else {
                println("funconExpr: ${funconExpr.text}, rewriteData: $rewriteData")
            }
        }
    }

    private fun processIntermediates(
        ruleDef: ExprContext, premise: PremiseExprContext, rewriteData: List<RewriteData>
    ): List<RewriteData> {
        val (lhs, rhs) = extractLhsRhs(premise)

        return when (premise) {
            is RewritePremiseContext -> {
                // TODO: Verify
                if (lhs is VariableContext && rhs !is VariableContext) {
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

            is TransitionPremiseContext,
            is TransitionPremiseWithControlEntityContext,
            is TransitionPremiseWithContextualEntityContext -> {
                val rewriteRhs = newVar()
                listOf(RewriteData(rhs, null, rewriteRhs))
            }

            is TransitionPremiseWithMutableEntityContext -> {
                val rewriteRhs = newVar()
                val entityRhs = newVar()
                listOf(
                    RewriteData(rhs, null, rewriteRhs),
                    RewriteData(premise.entityRhs.value, null, entityRhs)
                )
            }

            else -> listOf()
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
                if (lhs !is VariableContext) {
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)
                    val rewrite = "val $rewriteRhs = $rewriteLhs"
                    assignments.add(rewrite)
                }
            }

            is TransitionPremiseContext,
            is TransitionPremiseWithControlEntityContext,
            is TransitionPremiseWithContextualEntityContext,
            is TransitionPremiseWithMutableEntityContext -> {
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"
                assignments.add(rewrite)

                val condition = when {
                    lhs is VariableContext && rhs is VariableContext && rhs.text == lhs.text + "\'" -> {
                        // Base case, X usually rewrites to X'
                        "$rewriteLhs is $FCTNODE"
                    }

                    rhs is VariableContext && rhs.text == "_" -> {
                        // If executes to any non-null value
                        "$rewriteLhs is $FCTNODE && $rewriteRhs != null"
                    }

                    lhs is FunconExpressionContext -> {
                        // In the case of `atomic(X') --yielded( )->2 X''`
                        val lhsFuncon = getObject(lhs)
                        "$rewriteLhs is ${lhsFuncon.nodeName}"
                    }

                    lhs is VariableContext && rhs is VariableContext -> {
                        // In the case of `X --yielded( )-> V`
                        "$rewriteLhs == $rewriteLhs"
                    }

                    else -> throw DetailedException("Unexpected premise: ${premise.text}")
                }
                conditions.add(condition)
                if (premise is TransitionPremiseWithMutableEntityContext) {
                    val rewriteEntityLhs = rewrite(ruleDef, premise.entityLhs.value, rewriteData)
                    val rewriteEntityRhs = rewrite(ruleDef, premise.entityRhs.value, rewriteData)
                    val rewritten = "val $rewriteEntityRhs = $rewriteEntityLhs.execute(frame)"
                    assignments.add(rewritten)
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
                val condition =
                    if (rhs is VariableContext) {
                        val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)
                        "${rewriteRhs}.isInstance($rewriteLhs)"
                    } else if (rhs is ComplementExpressionContext && rhs.operand is VariableContext) {
                        val rewriteRhs = rewrite(ruleDef, rhs.operand, rewriteData)
                        "!${rewriteRhs}.isInstance($rewriteLhs)"
                    } else {
                        makeTypeCondition(rewriteLhs, premise.type)
                    }
                conditions.add(condition)
            }
        }
    }

    private fun processConclusion(
        ruleDef: ExprContext, conclusion: PremiseExprContext, rewriteData: List<RewriteData>
    ) {
        fun addAssignment(label: LabelContext, putStrFunc: (String, String) -> String) {
            val valueStr = if (label.value != null) {
                println("rewriting: ${label.text}")
                rewrite(ruleDef, label.value, rewriteData)
            } else "null"
            val assignment = putStrFunc(label.name.text, valueStr)
            assignments.add(assignment)
        }

        when (conclusion) {
            is TransitionPremiseWithControlEntityContext -> {
                val steps = conclusion.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                steps.forEach { step ->
                    step.labels().label().forEach { label -> addAssignment(label, ::putGlobalStr) }
                }
            }

            is TransitionPremiseWithContextualEntityContext -> addAssignment(conclusion.context_, ::putInScopeStr)
            is TransitionPremiseWithMutableEntityContext -> addAssignment(conclusion.entityRhs, ::putGlobalStr)
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

    private fun processEntities(
        ruleDef: ExprContext,
        premiseExpr: PremiseExprContext,
        isConclusion: Boolean = false
    ): List<RewriteData> {
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

        return labels.flatMap { label ->
            val labelObj = if (label.name.text == "abrupt") {
                // TODO: Edge case due to bug in CBS code for yield-on-value. Remove when fixed.
                globalObjects["abrupted"]
            } else {
                getObject(label)
            } as EntityObject

            if (isConclusion) {
                println("labelObjName: ${labelObj.name}, type: ${labelObj::class.simpleName}")
                val valueStr = if (label.value != null) {
                    rewrite(ruleDef, label.value)
                } else ""
                val star = if (labelObj.isIOEntity) "*" else ""
                val newEntityStr = "${labelObj.nodeName}($star$valueStr)"

                listOf(RewriteData(label.value, null, newEntityStr))
            } else {
                val getStr = labelObj.getFunc(label.name.text)
                getParamStrs(label, prefix = getStr)
            }
        }
    }

    init {
        val (ruleDef, toRewrite) = extractLhsRhs(conclusion)
        rewriteStr = if (toRewrite is TupleExpressionContext && toRewrite.exprs() == null) {
            if (returns.isNullable) "null" else "emptyArray()"
        } else {
            val rewriteData = mutableListOf<RewriteData>()

            // Add values for entities
            rewriteData.addAll((premises).flatMap { premise -> processEntities(ruleDef, premise) })
            rewriteData.addAll(processEntities(ruleDef, conclusion, isConclusion = true))

            // Process all intermediate values
            // TODO: Identify common intermediates, maybe outside the scope of a single Rule
            premises.forEach { premise -> rewriteData.addAll(processIntermediates(ruleDef, premise, rewriteData)) }

            // Add the type checking conditions
            // TODO: Remove redundant type conditions
            if (ruleDef is FunconExpressionContext) argsConditions(ruleDef, rewriteData)

            // Add data for premises and build conclusions
            premises.forEach { premise -> processPremises(ruleDef, premise, rewriteData) }

            // build rewrites from conclusions
            processConclusion(ruleDef, conclusion, rewriteData)

            rewrite(ruleDef, toRewrite, rewriteData)
        }
    }
}
