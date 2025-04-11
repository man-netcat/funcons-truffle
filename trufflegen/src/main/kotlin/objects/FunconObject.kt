package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.RewriteData
import main.exceptions.DetailedException
import main.exceptions.StringNotFoundException
import main.objects.AlgebraicDatatypeObject
import main.objects.EntityObject
import main.objects.Object
import org.antlr.v4.runtime.tree.ParseTree

abstract class AbstractFunconObject(
    ctx: ParseTree,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : Object(ctx, metaVariables) {

    val reducibleIndices = computeReducibles()
    override val keyWords: List<String> = listOf()

    protected fun computeReducibles(): List<Int> =
        params.mapIndexedNotNull { index, param -> if (!param.type.computes) index else null }

}

class FunconObject(
    ctx: FunconDefinitionContext,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val rewritesTo: ExprContext? = null,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : AbstractFunconObject(ctx, metaVariables) {

    val outerVariables = mutableMapOf<String, String>()
    private var intermediateCounter = 0
    private fun newVar() = "i${intermediateCounter++}"

    inner class Rule(
        premises: List<PremiseExprContext>,
        conclusion: PremiseExprContext,
        val metaVariableMap: Map<String, ExprContext>,
    ) {
        inner class Condition(val expr: String, val priority: Int = 1)

        val conditions = mutableListOf<Condition>()
        val entityVars = mutableSetOf<Object>()
        private val assignments = mutableListOf<String>()
        private val rewriteStr: String
        var rulePriority = 1

        val rewriteData: MutableList<RewriteData> = mutableListOf()

        val bodyStr: String
            get() = (assignments + rewriteStr).joinToString("\n")

        private fun addCondition(expr: String, priority: Int = 1) = conditions.add(Condition(expr, priority))
        internal fun getSortedConditions(): List<String> = conditions.sortedBy { it.priority }.map { it.expr }

        private fun argsConditions(funconExpr: FunconExpressionContext) {
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
                        val typeCondition = makeTypeCondition(paramStr, metaVariableMap[argType.text]!!)
                        addCondition(typeCondition)
                    }
                }

                when (argValue) {
                    is NumberContext -> addCondition("$paramStr == IntegerNode(${argValue.text})")
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
                            is SequenceExpressionContext -> 0
                            else -> 1
                        }
                    }
                    processArg(arg)
                }

                val offsetValue = sumVarargMin + nonSequenceArgs.size - (obj.params.size - 1)
                val condition = when {
                    sequenceArgs.isNotEmpty() -> when (offsetValue) {
                        1 -> {
                            rulePriority = 2
                            "get(${obj.sequenceIndex}).isNotEmpty()"
                        }

                        else -> if (sequenceArgs[0] is SequenceExpressionContext) {
                            "get(${obj.sequenceIndex}).isEmpty()"
                        } else {
                            "get(${obj.sequenceIndex}).size >= $offsetValue"
                        }
                    }

                    else -> when (offsetValue) {
                        0 -> {
                            rulePriority = 0
                            "get(${obj.sequenceIndex}).isEmpty()"
                        }

                        else -> "get(${obj.sequenceIndex}).size == $offsetValue"
                    }
                }
                addCondition(condition, priority = 0)
            } else {
//            println("funconExpr: ${funconExpr.text}, rewriteData: $rewriteData")
            }
        }

        fun makeRewriteDataObject(expr: ExprContext, str: String): RewriteData {
            return if (expr is TypeExpressionContext) {
                RewriteData(expr.value, expr.type, str)
            } else if (expr is NestedExpressionContext && expr.expr() is TypeExpressionContext) {
                val typeExpr = expr.expr() as TypeExpressionContext
                RewriteData(typeExpr.value, typeExpr.type, str)
            } else if (expr is SequenceExpressionContext) {
                RewriteData(null, null, str)
            } else {
                RewriteData(expr, null, str)
            }
        }

        private fun getControlEntityLabels(premiseExpr: TransitionPremiseWithControlEntityContext): List<Pair<LabelContext, EntityObject>> {
            val steps = premiseExpr.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
            return steps.flatMap { step ->
                step.labels().label().map { label -> label to labelToObject(label) }
            }
        }

        private fun processPremises(ruleDef: ExprContext, premise: PremiseExprContext) {
            val (lhs, rhs) = extractLhsRhs(premise)

            when (premise) {
                is RewritePremiseContext -> {
                    val rewrite = rewrite(ruleDef, lhs, rewriteData)
                    val variable = if (rewrite !in outerVariables.keys) {
                        val newVar = newVar()
                        outerVariables.put(rewrite, newVar)
                        newVar
                    } else outerVariables[rewrite]!!
                    val newRewriteData = makeRewriteDataObject(rhs, variable)
                    rewriteData.add(newRewriteData)
                }

                is TransitionPremiseWithContextualEntityContext -> {
                    val newRewriteData = RewriteData(rhs, null, newVar())
                    rewriteData.add(newRewriteData)

                    if (rhs.text == "_") return
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                    val label = premise.context_
                    val labelObj = labelToObject(label)

                    try {
                        val assignment = makeEntityAssignment(ruleDef, label, labelObj)
                        assignments.addFirst(assignment)
                    } catch (e: StringNotFoundException) {
                        entityVars.add(labelObj)

                        val newRewriteData = if (label.value is FunconExpressionContext) {
                            getParamStrs(label.value, prefix = labelObj.asVarName)
                        } else {
                            val newRewriteDataObject = makeRewriteDataObject(label.value, labelObj.asVarName)
                            listOf(newRewriteDataObject)
                        }
                        rewriteData.addAll(newRewriteData)
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
                    val newRewriteData = RewriteData(rhs, null, newVar())
                    rewriteData.add(newRewriteData)

                    if (rhs.text == "_") return
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                    val rewrite = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                    assignments.add(rewrite)

                    val condition = "$rewriteLhs.isReducible()"
                    addCondition(condition)

                }

                is TransitionPremiseWithControlEntityContext -> {
                    val newRewriteData = RewriteData(rhs, null, newVar())
                    rewriteData.add(newRewriteData)

                    if (rhs.text == "_") return
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                    val rewrite = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                    assignments.add(rewrite)

                    val labels = getControlEntityLabels(premise)
                    if (labels.isNotEmpty()) {
                        labels.forEach { (label, labelObj) ->
                            if (label.value == null) {
                                val emptyCondition = "${labelObj.asVarName}.isEmpty()"
                                addCondition(emptyCondition)
                                rulePriority = 0
                            } else {
                                val emptyCondition = "${labelObj.asVarName}.isNotEmpty()"
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
                    rewriteData.add(RewriteData(rhs, null, newVar()))
                    rewriteData.add(RewriteData(premise.entityRhs.value, null, newVar()))

                    if (rhs.text == "_") return
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)

                    val rewrite = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                    assignments.add(rewrite)

                    val label = premise.entityRhs
                    val labelObj = labelToObject(label)
                    if (label.value == null) {
                        val emptyCondition = "${labelObj.asVarName}.isEmpty()"
                        addCondition(emptyCondition)
                        rulePriority = 0
                    } else {
                        val emptyCondition = "${labelObj.asVarName}.isNotEmpty()"
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

        fun makeLabelRewrite(label: LabelContext, labelObj: EntityObject): List<RewriteData> {
            return if (label.value is FunconExpressionContext) {
                getParamStrs(label.value, prefix = labelObj.asVarName) + RewriteData(
                    null,
                    label.value,
                    labelObj.asVarName
                )
            } else if (label.value != null) {
                val newRewriteDataObject = makeRewriteDataObject(label.value, labelObj.asVarName)
                listOf(newRewriteDataObject)
            } else emptyList()
        }

        private fun makeEntityAssignment(
            ruleDef: ExprContext,
            label: LabelContext,
            labelObj: EntityObject,
        ): String {
            val valueStr = if (label.value != null && label.value.text != "_?") {
                rewrite(ruleDef, label.value, rewriteData)
            } else "SequenceNode()"
            return labelObj.putStr(valueStr)
        }

        private fun processConclusion(ruleDef: ExprContext, conclusion: PremiseExprContext) {
            when (conclusion) {
                is TransitionPremiseWithContextualEntityContext -> {
                    val label = conclusion.context_
                    val labelObj = labelToObject(label)
                    if (label.value == null) {
                        val emptyCondition = "${labelObj.asVarName}.isEmpty()"
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
                            val assignment = makeEntityAssignment(ruleDef, label, labelObj)
                            assignments.addFirst(assignment)
                        } catch (e: StringNotFoundException) {
                            entityVars.add(labelObj)
                            val newRewriteData = makeLabelRewrite(label, labelObj)
                            rewriteData.addAll(newRewriteData)
                        }
                    }
                }

                is TransitionPremiseWithMutableEntityContext -> {
                    val label = conclusion.entityLhs
                    val labelObj = labelToObject(label)
                    if (label.value == null) {
                        val emptyCondition = "${labelObj.asVarName}.isEmpty()"
                        rulePriority = 0
                        addCondition(emptyCondition, 0)
                    } else if (label.value.text == "_") {
                        val emptyCondition = "${labelObj.asVarName}.isNotEmpty() || ${labelObj.asVarName}.isEmpty()"
                        rulePriority = 1
                        addCondition(emptyCondition, 0)
                    } else {
                        val emptyCondition = "${labelObj.asVarName}.isNotEmpty()"
                        rulePriority = 2
                        addCondition(emptyCondition, 0)
                    }
                    entityVars.add(labelObj)

                    try {
                        val assignment = makeEntityAssignment(ruleDef, label, labelObj)
                        assignments.addFirst(assignment)
                    } catch (e: StringNotFoundException) {
                        entityVars.add(labelObj)
                        val newRewriteData = makeLabelRewrite(label, labelObj)
                        rewriteData.addAll(newRewriteData)
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
        ) {
            val labels = when {
                premiseExpr is TransitionPremiseWithContextualEntityContext && !isPremise ->
                    listOf(premiseExpr.context_ to labelToObject(premiseExpr.context_))

                premiseExpr is TransitionPremiseWithControlEntityContext && isPremise && !emptyPremises ->
                    getControlEntityLabels(premiseExpr)

                premiseExpr is TransitionPremiseWithMutableEntityContext ->
                    listOf(premiseExpr.entityLhs to labelToObject(premiseExpr.entityLhs))

                else -> emptyList()
            }

            val newRewriteData = labels.flatMap { (label, labelObj) -> makeLabelRewrite(label, labelObj) }
            rewriteData.addAll(newRewriteData)
        }

        init {
            val (ruleDef, toRewrite) = extractLhsRhs(conclusion)

            // Add values for entities
            premises.forEach { premise -> processEntities(premise) }
            fun isTransitionPremise(premiseExpr: PremiseExprContext): Boolean = when (premiseExpr) {
                is TransitionPremiseContext,
                is TransitionPremiseWithMutableEntityContext,
                is TransitionPremiseWithControlEntityContext,
                is TransitionPremiseWithContextualEntityContext,
                    -> true

                else -> false
            }
            processEntities(conclusion, false, !premises.any(::isTransitionPremise))

            // Add data for premises and build conclusions
            premises.forEach { premise -> processPremises(ruleDef, premise) }

            // build rewrites from conclusions
            processConclusion(ruleDef, conclusion)

            // Add the type checking conditions
            if (ruleDef is FunconExpressionContext) argsConditions(ruleDef)

            rewriteStr = rewrite(ruleDef, toRewrite, rewriteData)
        }
    }

    override val contentStr: String
        get() {
            val newChildren = mutableListOf<String>()
            val stringBuilder = StringBuilder()

            val returnStr = "return " + when {
                rewritesTo != null -> rewrite(ctx, rewritesTo)
                rules.isNotEmpty() -> {
                    val ruleObjs = rules.map { rule ->
                        val premises = rule.premises()?.premiseExpr()?.toList() ?: emptyList()
                        Rule(premises, rule.conclusion, metavariableMap)
                    }
                    val entityVars = buildString {
                        ruleObjs.flatMap { it.entityVars }.distinct().forEach { entityObj ->
                            entityObj as EntityObject
                            appendLine(makeVariable(entityObj.asVarName, entityObj.getStr()))
                        }
                    }

                    val pairs = ruleObjs.sortedBy { it.rulePriority }
                        .map { it.getSortedConditions().joinToString(" && ") to it.bodyStr }

                    if (entityVars.isNotEmpty()) stringBuilder.appendLine(entityVars)

                    outerVariables.forEach { (rewrite, varName) ->
                        var initVarName = "${varName}Init"
                        newChildren.add("@Child private lateinit var $initVarName: $TERMNODE")
                        val insertStr = "$initVarName = insert($rewrite)"
                        val insertIf = makeIfStatement(listOf("!::$initVarName.isInitialized" to insertStr))
                        stringBuilder.appendLine(insertStr)
                        val rewriteStr = makeVariable(varName, "$initVarName.rewrite(frame)")
                        stringBuilder.appendLine(rewriteStr)
                    }

                    makeWhenStatement(pairs, elseBranch = "FailNode()")
                }

                else -> throw DetailedException("Funcon $name does not have any associated rules.")
            }

            stringBuilder.appendLine(returnStr)

            val outerStringBuilder = StringBuilder()
            if (newChildren.isNotEmpty()) {
                newChildren.forEach { newChild ->
                    outerStringBuilder.appendLine(newChild)
                }
                outerStringBuilder.appendLine()
            }
            val reduceFunc = makeReduceFunction(stringBuilder.toString(), TERMNODE)
            outerStringBuilder.appendLine(reduceFunc)
            return outerStringBuilder.toString()
        }

    override val keyWords: List<String> = emptyList()
}

class DatatypeFunconObject(
    ctx: FunconExpressionContext,
    internal val superclass: AlgebraicDatatypeObject,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : AbstractFunconObject(ctx, metaVariables) {
    override val superClassStr: String get() = makeFunCall(if (reducibleIndices.isEmpty()) superclass.nodeName else TERMNODE)
    override val keyWords: List<String> = emptyList()

    override val contentStr: String
        get() {
            return if (reducibleIndices.isNotEmpty()) {
                val reduceBuilder = StringBuilder()
                val returnStr = "return Value$nodeName(${params.joinToString { param -> "p${param.index}" }})"
                reduceBuilder.appendLine(returnStr)
                makeReduceFunction(reduceBuilder.toString(), TERMNODE)
            } else ""
        }
}
