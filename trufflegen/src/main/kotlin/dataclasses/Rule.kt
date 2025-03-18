package main.dataclasses

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException
import main.exceptions.StringNotFoundException
import main.objects.EntityObject
import main.objects.Object

class Rule(premises: List<PremiseExprContext>, conclusion: PremiseExprContext, returns: Type) {
    val conditions = mutableListOf<String>()
    val emptyConditions = mutableListOf<String>()
    val entityVars = mutableSetOf<Object>()
    private val assignments = mutableListOf<String>()
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

    private fun argsConditions(
        funconExpr: FunconExpressionContext,
        conclusion: PremiseExprContext,
        rewriteData: MutableList<RewriteData>,
    ) {
        val obj = getObject(funconExpr)
        val args = extractArgs(funconExpr)
        val labels = getEntities(conclusion)
        if (obj.params.size == 1 && args.isEmpty()) {
            if (obj.params[0].type.isSequence) {
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
                    is ComplementExpressionContext,
                        -> {
                        val typeCondition = makeTypeCondition(paramStr, argType)
                        conditions.add(typeCondition)
                    }

                    is VariableContext -> {}
                }

                when (argValue) {
                    is NumberContext -> conditions.add("$paramStr == ${argValue.text}")
                    is TupleExpressionContext -> conditions.add("${paramStr}.isEmpty()")
                    is SuffixExpressionContext -> {}
                }
            }
        }

        if (obj.hasSequence) {
            val (sequenceArgs, nonSequenceArgs) = partitionArgs(args)

            val sumVarargMin = sequenceArgs.sumOf { arg ->
                fun processArg(arg: ExprContext): Int {
                    return when (arg) {
                        is TypeExpressionContext -> processArg(arg.value)
                        is SuffixExpressionContext -> if (arg.op.text == "+") 1 else 0
                        else -> 1
                    }
                }
                processArg(arg)
            }

            val sequenceParamStr = obj.sequenceParam!!.name
            val offsetValue = sumVarargMin + nonSequenceArgs.size - (obj.params.size - 1)
            val condition = if (sequenceArgs.isNotEmpty()) {
                "$sequenceParamStr.size >= $offsetValue"
            } else {
                "$sequenceParamStr.size == $offsetValue"
            }
            conditions.addFirst(condition)
        } else {
            println("funconExpr: ${funconExpr.text}, rewriteData: $rewriteData")
        }
    }

    private fun processIntermediates(
        ruleDef: ExprContext, premise: PremiseExprContext, rewriteData: MutableList<RewriteData>,
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
            is TransitionPremiseWithContextualEntityContext,
                -> {
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
        rewriteData: MutableList<RewriteData>,
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
            is TransitionPremiseWithMutableEntityContext,
                -> {
                if (rhs.text == "_") return
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                val rewrite = "val $rewriteRhs = $rewriteLhs.reduce(frame)"
                assignments.add(rewrite)

                val condition = when {
                    rhs is VariableContext -> {
                        "$rewriteLhs !is ValuesNode"
                    }

                    lhs is FunconExpressionContext -> {
                        // In the case of `atomic(X') --yielded( )->2 X''`
                        val lhsFuncon = getObject(lhs)
                        "$rewriteLhs is ${lhsFuncon.nodeName}"
                    }

                    else -> throw DetailedException("Unexpected premise: ${premise.text}")
                }
                conditions.add(condition)
                if (premise is TransitionPremiseWithMutableEntityContext) {
                    val rewriteEntityLhs = rewrite(ruleDef, premise.entityLhs.value, rewriteData)
                    val rewriteEntityRhs = rewrite(ruleDef, premise.entityRhs.value, rewriteData)
                    val rewritten = "val $rewriteEntityRhs = $rewriteEntityLhs.reduce(frame)"
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
                        "${rewriteRhs}.instanceOf($rewriteLhs as TypesNode)"
                    } else if (rhs is ComplementExpressionContext && rhs.operand is VariableContext) {
                        val rewriteRhs = rewrite(ruleDef, rhs.operand, rewriteData)
                        "!${rewriteRhs}.instanceOf($rewriteLhs as TypesNode)"
                    } else {
                        makeTypeCondition(rewriteLhs, premise.type)
                    }
                conditions.add(condition)
            }
        }
    }

    private fun getEntities(premiseExpr: PremiseExprContext): List<Pair<LabelContext, EntityObject>> {
        fun labelToObject(label: LabelContext): EntityObject {
            return if (label.name.text == "abrupt") {
                // TODO: Edge case due to bug in CBS code for yield-on-value. Remove when fixed.
                globalObjects["abrupted"]
            } else {
                getObject(label)
            } as EntityObject
        }

        return when (premiseExpr) {
            is TransitionPremiseWithControlEntityContext -> {
                val steps = premiseExpr.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                steps.flatMap { step ->
                    step.labels().label().map { label -> label to labelToObject(label) }
                }
            }

            is TransitionPremiseWithMutableEntityContext -> listOf(premiseExpr.entityLhs to labelToObject(premiseExpr.entityLhs))
            is TransitionPremiseWithContextualEntityContext -> listOf(premiseExpr.context_ to labelToObject(premiseExpr.context_))
            else -> listOf<Pair<LabelContext, EntityObject>>()
        }
    }

    private fun processConclusion(
        ruleDef: ExprContext, conclusion: PremiseExprContext, rewriteData: MutableList<RewriteData>,
    ) {
        val labels = getEntities(conclusion)
        labels.forEach { (label, labelObj) ->
            try {
                val valueStr = if (label.value != null) {
                    rewrite(ruleDef, label.value, rewriteData)
                } else "EmptySequenceNode()"
                val newEntityStr = "${labelObj.nodeName}($valueStr)"
                val assignment = labelObj.putStr(newEntityStr)
                assignments.addFirst(assignment)
            } catch (e: StringNotFoundException) {
                // Likely a read-only entity
                val condition = if (label.value == null) {
                    "${labelObj.getStr()} == null"
                } else {
                    "${labelObj.getStr()} != null"
                }
                conditions.add(condition)
                val getStr = labelObj.getStr()
                rewriteData.addAll(getParamStrs(label, prefix = getStr))
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

    private fun processEntities(premiseExpr: PremiseExprContext): List<RewriteData> {
        val labels = getEntities(premiseExpr)

        return labels.flatMap { (label, labelObj) ->
            entityVars.add(labelObj)
            val qmark = if (labelObj.entityType == EntityType.CONTROL) "?" else ""
            getParamStrs(label, prefix = labelObj.asVarName + qmark)
        }
    }

    init {
        val (ruleDef, toRewrite) = extractLhsRhs(conclusion)
        rewriteStr = if (toRewrite is TupleExpressionContext && toRewrite.exprs() == null) {
            if (returns.isNullable) "null" else "emptyArray()"
        } else {
            val rewriteData = mutableListOf<RewriteData>()

            // Add values for entities
            rewriteData.addAll((premises).flatMap { premise -> processEntities(premise) })
//            rewriteData.addAll(processEntities(conclusion))

            // Process all intermediate values
            // TODO: Identify common intermediates, maybe outside the scope of a single Rule
            premises.forEach { premise -> rewriteData.addAll(processIntermediates(ruleDef, premise, rewriteData)) }

            // Add the type checking conditions
            if (ruleDef is FunconExpressionContext) argsConditions(ruleDef, conclusion, rewriteData)

            // Add data for premises and build conclusions
            premises.forEach { premise -> processPremises(ruleDef, premise, rewriteData) }

            // build rewrites from conclusions
            processConclusion(ruleDef, conclusion, rewriteData)

            rewrite(ruleDef, toRewrite, rewriteData)
        }
    }
}
