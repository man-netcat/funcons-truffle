package main.dataclasses

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException
import main.exceptions.StringNotFoundException
import main.objects.AlgebraicDatatypeObject
import main.objects.EntityObject
import main.objects.Object
import main.objects.TypeObject
import objects.DatatypeFunconObject

class Rule(
    premises: List<PremiseExprContext>,
    conclusion: PremiseExprContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
    val outerVariables: MutableMap<String, String>,
) {
    data class Condition(val expr: String, val priority: Int = 1)

    val conditions = mutableListOf<Condition>()
    val entityVars = mutableSetOf<Object>()
    private val assignments = mutableListOf<String>()
    private val rewriteStr: String
    private var intermediateCounter = 0
    private val metavariableMap = metaVariables.associate { (variable, type) -> variable.text to type }
    var rulePriority = 1

    val bodyStr: String
        get() = (assignments + rewriteStr).joinToString("\n")

    private fun addCondition(expr: String, priority: Int = 1) {
        conditions.add(Condition(expr, priority))
    }

    internal fun getSortedConditions(): List<String> {
        return conditions.sortedBy { it.priority }.map { it.expr }
    }

    private fun newVar() = "i${intermediateCounter++}"

    private fun makeTypeCondition(paramStr: String, typeExpr: ExprContext): String {
        val (obj, isComplement) = when (typeExpr) {
            is ComplementExpressionContext -> getObject(typeExpr.expr()) to true
            else -> getObject(typeExpr) to false
        }

        val complementStr = if (isComplement) "!" else ""
        return when {
            obj is AlgebraicDatatypeObject || (obj is TypeObject && obj.operator != "~>")
                -> "${complementStr}$paramStr.isIn${obj.camelCaseName}()"

            else -> {
                val explicitValue = if (obj is DatatypeFunconObject && obj.params.isNotEmpty()) "Value" else ""
                "$paramStr ${complementStr}is $explicitValue${obj.nodeName}"
            }
        }
    }

    private fun argsConditions(funconExpr: FunconExpressionContext, rewriteData: MutableList<RewriteData>) {
        val obj = getObject(funconExpr)
        val args = extractArgs(funconExpr)

        val paramStrs = getParamStrs(funconExpr)
        (paramStrs + rewriteData).forEach { data ->
            val (argValue, argType, paramStr) = data

            if (argType == null && argValue == null) {
                rulePriority = 0
                addCondition("${paramStr}.isEmpty()")
            }

            when (argType) {
                is SuffixExpressionContext -> if (argType.op.text == "+") {
                    // If it's an expression of the type "X+" it cannot be empty.
                    val typeCondition = "${paramStr}.isNotEmpty()"
                    addCondition(typeCondition)
                }

                is FunconExpressionContext,
                is ListExpressionContext,
                is SetExpressionContext,
                is ComplementExpressionContext,
                    -> {
                    val typeCondition = makeTypeCondition(paramStr, argType)
                    addCondition(typeCondition)
                }

                is VariableContext -> {
                    val typeCondition = makeTypeCondition(paramStr, metavariableMap[argType.text]!!)
                    addCondition(typeCondition)
                }
            }

            when (argValue) {
                is NumberContext -> addCondition("$paramStr == ${argValue.text}")
                is SuffixExpressionContext -> {}
            }
        }

        if (obj.hasSequence) {
            val (sequenceArgs, nonSequenceArgs) = partitionArgs(args)

            val sumVarargMin = sequenceArgs.sumOf { arg ->
                fun processArg(arg: ExprContext): Int {
                    return when (arg) {
                        is TypeExpressionContext -> processArg(arg.value)
                        is SuffixExpressionContext -> if (arg.op.text == "+") 1 else 0
                        is TupleExpressionContext -> 0
                        else -> 1
                    }
                }
                processArg(arg)
            }

            val sequenceParamStr = "get(${obj.sequenceIndex})"
            val offsetValue = sumVarargMin + nonSequenceArgs.size - (obj.params.size - 1)
            val condition = when {
                sequenceArgs.isNotEmpty() -> when (offsetValue) {
                    1 -> {
                        rulePriority = 2
                        "$sequenceParamStr.isNotEmpty()"
                    }

                    else -> if (sequenceArgs[0] is TupleExpressionContext) {
                        "$sequenceParamStr.isEmpty()"
                    } else {
                        "$sequenceParamStr.size >= $offsetValue"
                    }
                }

                else -> when (offsetValue) {
                    0 -> {
                        rulePriority = 0
                        "$sequenceParamStr.isEmpty()"
                    }

                    else -> "$sequenceParamStr.size == $offsetValue"
                }
            }
            addCondition(condition, priority = 0)
        } else {
//            println("funconExpr: ${funconExpr.text}, rewriteData: $rewriteData")
        }
    }

    private fun processIntermediates(
        ruleDef: ExprContext, premise: PremiseExprContext, rewriteData: MutableList<RewriteData>,
    ): List<RewriteData> {
        val (lhs, rhs) = extractLhsRhs(premise)

        return when (premise) {
            is RewritePremiseContext -> {
                val rewrite = rewrite(ruleDef, lhs, rewriteData)
                val variable = if (rewrite !in outerVariables.keys) {
                    val newVar = newVar()
                    outerVariables.put(rewrite, newVar)
                    newVar
                } else outerVariables[rewrite]!!
                val rewriteData =
                    if (rhs is NestedExpressionContext && rhs.expr() is TypeExpressionContext) {
                        val typeExpr = rhs.expr() as TypeExpressionContext
                        RewriteData(typeExpr.value, typeExpr.type, variable)
                    } else if (rhs is TupleExpressionContext) {
                        RewriteData(null, null, variable)
                    } else {
                        RewriteData(rhs, null, variable)
                    }
                listOf(rewriteData)
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

    private fun getControlEntityLabels(premiseExpr: TransitionPremiseWithControlEntityContext): List<Pair<LabelContext, EntityObject>> {
        val steps = premiseExpr.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
        return steps.flatMap { step ->
            step.labels().label().map { label -> label to labelToObject(label) }
        }
    }

    private fun processPremises(
        ruleDef: ExprContext,
        premise: PremiseExprContext,
        rewriteData: MutableList<RewriteData>,
    ) {
        val (lhs, rhs) = extractLhsRhs(premise)

        when (premise) {
            is TransitionPremiseWithContextualEntityContext -> {
                if (rhs.text == "_") return
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)


                val label = premise.context_
                val labelObj = getObject(label) as EntityObject

                try {
                    val assignment = makeEntityAssignment(ruleDef, label, labelObj, rewriteData)
                    assignments.addFirst(assignment)
                } catch (e: StringNotFoundException) {
                    entityVars.add(labelObj)
                    rewriteData.addAll(getParamStrs(label, prefix = labelObj.asVarName))
                }

                val rewrite = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                assignments.add(rewrite)

                val condition = when {
                    rhs is VariableContext -> "$rewriteLhs.isReducible()"
                    else -> throw DetailedException("Unexpected premise: ${premise.text}")
                }
                addCondition(condition)
            }

            is TransitionPremiseContext -> {
                if (rhs.text == "_") return
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                val rewrite = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                assignments.add(rewrite)

                val condition = "$rewriteLhs.isReducible()"
                addCondition(condition)
            }

            is TransitionPremiseWithControlEntityContext,
                -> {

                if (rhs.text == "_") return
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                val rewrite = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                assignments.add(rewrite)

                val labels = getControlEntityLabels(premise)
                if (labels.isNotEmpty()) {
                    labels.forEach { (label, labelObj) ->
                        if (label.value == null) {
                            val emptyCondition = "${labelObj.asVarName}.emptyValue()"
                            addCondition(emptyCondition)
                            rulePriority = 0
                        } else {
                            val emptyCondition = "${labelObj.asVarName}.hasValue()"
                            addCondition(emptyCondition, 0)
                            rulePriority = 2
                        }
                        entityVars.add(labelObj)
                    }
                }

                val condition = when {
                    rhs is VariableContext -> "$rewriteLhs.isReducible()"
                    lhs is FunconExpressionContext -> {
                        // In the case of `atomic(X') --yielded( )->2 X''`
                        val lhsFuncon = getObject(lhs)
                        "$rewriteLhs is ${lhsFuncon.nodeName}"
                    }

                    else -> throw DetailedException("Unexpected premise: ${premise.text}")
                }
                addCondition(condition)
            }

            is TransitionPremiseWithMutableEntityContext -> {
                if (rhs.text == "_") return
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                val rewrite = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                assignments.add(rewrite)

                val label = premise.entityRhs
                val labelObj = getObject(label)
                if (label.value == null) {
                    val emptyCondition = "${labelObj.asVarName}.emptyValue()"
                    addCondition(emptyCondition)
                    rulePriority = 0
                } else {
                    val emptyCondition = "${labelObj.asVarName}.hasValue()"
                    addCondition(emptyCondition, 0)
                    rulePriority = 2
                }
                entityVars.add(labelObj)

                val condition = "$rewriteLhs.isReducible()"
                addCondition(condition)

                val rewriteEntityLhs = rewrite(ruleDef, premise.entityLhs.value, rewriteData)
                val rewriteEntityRhs = rewrite(ruleDef, premise.entityRhs.value, rewriteData)
                val entityRewrite = makeVariable(rewriteEntityRhs, "$rewriteEntityLhs.reduce(frame)")

                assignments.add(entityRewrite)
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
                addCondition(condition, priority = 2)
            }

            is TypePremiseContext -> {
                val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                val condition =
                    if (rhs is VariableContext) {
                        val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)
                        "${rewriteLhs}.isInType($rewriteRhs)"
                    } else if (rhs is ComplementExpressionContext && rhs.operand is VariableContext) {
                        val rewriteRhs = rewrite(ruleDef, rhs.operand, rewriteData)
                        "!${rewriteLhs}.isInType($rewriteRhs)"
                    } else {
                        makeTypeCondition(rewriteLhs, premise.type)
                    }
                addCondition(condition, priority = 2)
            }
        }
    }

    fun labelToObject(label: LabelContext): EntityObject {
        return if (label.name.text == "abrupt") {
            // TODO: Edge case due to bug in CBS code for yield-on-value. Remove when fixed.
            globalObjects["abrupted"]
        } else {
            getObject(label)
        } as EntityObject
    }

    private fun makeEntityAssignment(
        ruleDef: ExprContext,
        label: LabelContext,
        labelObj: EntityObject,
        rewriteData: MutableList<RewriteData>,
    ): String {
        val valueStr = if (label.value != null && label.value.text != "_?") {
            "${rewrite(ruleDef, label.value, rewriteData)}.toSequence()"
        } else "SequenceNode()"
        val newEntityStr = "${labelObj.entityName}($valueStr)"
        return labelObj.putStr(newEntityStr)
    }

    private fun processConclusion(
        ruleDef: ExprContext,
        conclusion: PremiseExprContext,
        rewriteData: MutableList<RewriteData>,
        emptyPremises: Boolean = false,
    ) {
        when (conclusion) {
            is TransitionPremiseWithContextualEntityContext -> {
                val label = conclusion.context_
                val labelObj = getObject(label) as EntityObject
                if (label.value == null) {
                    val emptyCondition = "${labelObj.asVarName}.emptyValue()"
                    rulePriority = 0
                    addCondition(emptyCondition, 0)
                }
                entityVars.add(labelObj)
            }

            is TransitionPremiseWithControlEntityContext -> {
                val steps = conclusion.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                val labels = steps.flatMap { step ->
                    step.labels().label().map { label -> label to labelToObject(label) }
                }
                labels.forEach { (label, labelObj) ->
                    try {
                        val assignment = makeEntityAssignment(ruleDef, label, labelObj, rewriteData)
                        assignments.addFirst(assignment)
                    } catch (e: StringNotFoundException) {
                        entityVars.add(labelObj)
                        rewriteData.addAll(getParamStrs(label, prefix = labelObj.asVarName))
                    }
                }
            }

            is TransitionPremiseWithMutableEntityContext -> {
                val label = conclusion.entityLhs
                val labelObj = getObject(label) as EntityObject
                if (label.value == null) {
                    val emptyCondition = "${labelObj.asVarName}.emptyValue()"
                    rulePriority = 0
                    addCondition(emptyCondition, 0)
                } else if (label.value.text == "_") {
                    val emptyCondition = "${labelObj.asVarName}.hasValue() || ${labelObj.asVarName}.emptyValue()"
                    rulePriority = 1
                    addCondition(emptyCondition, 0)
                } else {
                    val emptyCondition = "${labelObj.asVarName}.hasValue()"
                    rulePriority = 2
                    addCondition(emptyCondition, 0)
                }
                entityVars.add(labelObj)

                try {
                    val assignment = makeEntityAssignment(ruleDef, label, labelObj, rewriteData)
                    assignments.addFirst(assignment)
                } catch (e: StringNotFoundException) {
                    entityVars.add(labelObj)
                    rewriteData.addAll(getParamStrs(label, prefix = labelObj.asVarName))
                }
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

    private fun processEntities(
        premiseExpr: PremiseExprContext,
        isPremise: Boolean = true,
        emptyPremises: Boolean = false,
    ): List<RewriteData> {
        val labels = when {
            premiseExpr is TransitionPremiseWithContextualEntityContext && !isPremise && emptyPremises ->
                listOf(premiseExpr.context_ to labelToObject(premiseExpr.context_))

            premiseExpr is TransitionPremiseWithControlEntityContext && isPremise && !emptyPremises ->
                getControlEntityLabels(premiseExpr)

            premiseExpr is TransitionPremiseWithMutableEntityContext ->
                listOf(premiseExpr.entityLhs to labelToObject(premiseExpr.entityLhs))

            else -> emptyList()
        }

        return labels.flatMap { (label, labelObj) ->
            if (label.value != null) {
                entityVars.add(labelObj)
                getParamStrs(label, prefix = labelObj.asVarName)
            } else emptyList()
        }
    }

    init {
        val (ruleDef, toRewrite) = extractLhsRhs(conclusion)

        val rewriteData = mutableListOf<RewriteData>()

        // Add values for entities
        rewriteData.addAll((premises).flatMap { premise -> processEntities(premise) })
        fun isTransitionPremise(premiseExpr: PremiseExprContext): Boolean = when (premiseExpr) {
            is TransitionPremiseContext,
            is TransitionPremiseWithMutableEntityContext,
            is TransitionPremiseWithControlEntityContext,
            is TransitionPremiseWithContextualEntityContext,
                -> true

            else -> false
        }
        rewriteData.addAll(processEntities(conclusion, false, !premises.any(::isTransitionPremise)))

        // Process all intermediate values
        // TODO: Identify common intermediates, maybe outside the scope of a single Rule
        premises.forEach { premise -> rewriteData.addAll(processIntermediates(ruleDef, premise, rewriteData)) }

        // Add data for premises and build conclusions
        premises.forEach { premise -> processPremises(ruleDef, premise, rewriteData) }

        // build rewrites from conclusions
        processConclusion(ruleDef, conclusion, rewriteData)

        // Add the type checking conditions
        if (ruleDef is FunconExpressionContext) argsConditions(ruleDef, rewriteData)

        rewriteStr = rewrite(ruleDef, toRewrite, rewriteData)
    }
}
