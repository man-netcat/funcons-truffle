package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.RewriteData
import main.exceptions.DetailedException
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

data class Condition(val expr: String, val priority: Int = 1)

class FunconObject(
    ctx: FunconDefinitionContext,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val rewritesTo: ExprContext? = null,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : AbstractFunconObject(ctx, metaVariables) {

    inner class VariableGenerator() {
        val prefixMap = mutableMapOf<String, Int>()
        val variables = mutableMapOf<String, String>()

        private fun newVar(prefix: String): String {
            return if (prefix in prefixMap.keys) {
                val newVar = "$prefix${prefixMap[prefix]}"
                prefixMap[prefix] = prefixMap[prefix]!! + 1
                return newVar
            } else {
                val newVar = "${prefix}0"
                prefixMap[prefix] = 1
                newVar
            }
        }

        fun getVar(rewrite: String, prefix: String): String {
            return if (rewrite !in variables.keys) {
                val newVar = newVar(prefix)
                variables.put(rewrite, newVar)
                newVar
            } else variables[rewrite]!!
        }
    }

    val variables = VariableGenerator()

    var contextualWrite = ""
    var contextualRead = ""
    var mutableWrite = ""
    var mutableRead = ""
    val controlReads = mutableSetOf<String>()

    inner class Rule(
        premises: List<PremiseExprContext>,
        conclusion: PremiseExprContext,
        val metaVariableMap: Map<String, ExprContext>,
    ) {
        val controlWrites = mutableSetOf<String>()
        var stepVariable: Pair<String, String>? = null

        val conditions = mutableSetOf<Condition>()
        private val rewriteStr: String
        var rulePriority = 1

        val rewriteData: MutableList<RewriteData> = mutableListOf()

        val bodyStr: String
            get() = (controlWrites + listOf(mutableWrite) + listOf(rewriteStr)).filter { it.isNotEmpty() }
                .joinToString("\n")

        private fun addCondition(expr: String, priority: Int = 1) = conditions.add(Condition(expr, priority))
        internal fun getSortedConditions(): String =
            conditions.sortedBy { it.priority }.joinToString(" && ") { it.expr }

        private fun argsConditions(funconExpr: FunconExpressionContext) {
            val paramStrs = getParamStrs(funconExpr)
            (paramStrs + rewriteData).forEach { data ->
                val (argValue, argType, paramStr) = data

                when (argType) {
                    is SuffixExpressionContext -> if (argType.op.text == "+") {
                        // If it's an expression of the type "X+" it cannot be empty.
                        val typeCondition = "${paramStr}.isNotEmpty()"
                        rulePriority = 2
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

                if (argValue is NumberContext) addCondition("$paramStr == IntegerNode(${argValue.text})")

                if (data.sizeCondition != null) {
                    val (condition, priority) = data.sizeCondition
                    addCondition(condition)
                    rulePriority = priority
                }
            }

            val sizeCondition = makeSizeCondition(funconExpr, "")
            if (sizeCondition != null) {
                val (condition, priority) = sizeCondition
                addCondition(condition)
                rulePriority = priority
            }
        }

        fun makeRewriteDataObject(expr: ExprContext, str: String): RewriteData {
            return if (expr is TypeExpressionContext) {
                RewriteData(expr.value, expr.type, str)
            } else if (expr is NestedExpressionContext && expr.expr() is TypeExpressionContext) {
                val typeExpr = expr.expr() as TypeExpressionContext
                RewriteData(typeExpr.value, typeExpr.type, str)
            } else if (expr is SequenceExpressionContext) {
                RewriteData(null, null, str, "$str.isEmpty()" to 0)
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
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val rewriteRhs = variables.getVar(rewriteLhs, "r")
                    val newRewriteData = makeRewriteDataObject(rhs, rewriteRhs)
                    rewriteData.add(newRewriteData)
                }

                is TransitionPremiseWithMutableEntityContext,
                    -> {
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val rewriteRhs = variables.getVar(rewriteLhs, "r")
                    val newRewriteData = makeRewriteDataObject(rhs, rewriteRhs)
                    rewriteData.add(newRewriteData)

                    val label = premise.entityRhs
                    val labelObj = labelToObject(label)
                    val newNewRewriteData = makeRewriteDataObject(label.value, labelObj.asVarName)
                    rewriteData.add(newNewRewriteData)
                }

                is TransitionPremiseContext,
                is TransitionPremiseWithContextualEntityContext,
                is TransitionPremiseWithControlEntityContext,
                    -> {
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val rewriteRhs = variables.getVar(rewriteLhs, "s")
                    val newRewriteData = makeRewriteDataObject(rhs, rewriteRhs)
                    rewriteData.add(newRewriteData)

                    when (premise) {
                        is TransitionPremiseWithContextualEntityContext -> {
                            val label = premise.context_
                            val labelObj = labelToObject(label)
                            contextualWrite = makeEntityAssignment(ruleDef, label, labelObj)
                        }

                        is TransitionPremiseWithControlEntityContext -> {
                            val labels = getControlEntityLabels(premise)
                            labels.forEach { (label, labelObj) ->
                                processEntityCondition(label)
                                var controlRead = makeVariable(labelObj.asVarName, labelObj.getStr())
                                controlReads.add(controlRead)
                            }
                        }
                    }

                    stepVariable = rewriteLhs to rewriteRhs
                }

                is BooleanPremiseContext -> {
                    val rewriteLhs = rewrite(ruleDef, lhs, rewriteData)
                    val lhsVar = variables.getVar(rewriteLhs, "r")
                    val lhsRewriteData = makeRewriteDataObject(lhs, lhsVar)
                    rewriteData.add(lhsRewriteData)

                    val rewriteRhs = rewrite(ruleDef, rhs, rewriteData)
                    val rhsVar = variables.getVar(rewriteRhs, "r")
                    val rhsRewriteData = makeRewriteDataObject(rhs, rhsVar)
                    rewriteData.add(rhsRewriteData)

                    val op = when (premise.op.text) {
                        "==" -> "=="
                        "=/=" -> "!="
                        else -> throw DetailedException("Unexpected operator type: ${premise.op.text}")
                    }
                    val condition = "$lhsVar $op $rhsVar"
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
                val rewriteStr = rewrite(ruleDef, label.value, rewriteData)
                val variable = variables.getVar(rewriteStr, "r")
                val newRewriteData = makeRewriteDataObject(label.value, variable)
                rewriteData.add(newRewriteData)
                variable
            } else "SequenceNode()"
            return labelObj.putStr(valueStr)
        }

        private fun processEntityCondition(label: LabelContext) {
            val labelObj = labelToObject(label)
            if (label.value == null) {
                val emptyCondition = "${labelObj.asVarName}.isEmpty()"
                rulePriority = 0
                addCondition(emptyCondition, 0)
            } else if (label.value.text !in listOf("_", "_?")) {
                val emptyCondition = "${labelObj.asVarName}.isNotEmpty()"
                rulePriority = 2
                addCondition(emptyCondition, 0)
            }
        }

        private fun processConclusion(ruleDef: ExprContext, conclusion: PremiseExprContext) {
            when (conclusion) {
                is TransitionPremiseWithContextualEntityContext -> {
                    val label = conclusion.context_
                    if (label.value?.text != "_?") {
                        val labelObj = labelToObject(label)
                        processEntityCondition(label)
                        contextualRead = makeVariable(labelObj.asVarName, labelObj.getStr())
                    }
                }

                is TransitionPremiseWithControlEntityContext -> {
                    val steps = conclusion.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                    steps.forEach { step ->
                        step.labels().label().forEach { label ->
                            val labelObj = labelToObject(label)
                            val valueStr = if (label.value != null && label.value.text != "_?") {
                                rewrite(ruleDef, label.value, rewriteData)
                            } else "SequenceNode()"
                            val controlWrite = labelObj.putStr(valueStr)
                            controlWrites.add(controlWrite)
                        }
                    }
                }

                is TransitionPremiseWithMutableEntityContext -> {
                    val labelLhs = conclusion.entityLhs
                    val labelLhsObj = labelToObject(labelLhs)
                    mutableRead = makeVariable(labelLhsObj.asVarName, labelLhsObj.getStr())

                    val labelRhs = conclusion.entityRhs
                    if (labelRhs.text != labelLhs.text) {
                        val labelRhsObj = labelToObject(labelRhs)
                        mutableWrite = makeEntityAssignment(ruleDef, labelRhs, labelRhsObj)
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

                    if (ruleObjs.map { it.conditions }.toSet().size != ruleObjs.size) {
                        throw DetailedException("rules for $name are not distinct.")
                    }

                    if (contextualRead.isNotEmpty()) stringBuilder.appendLine(contextualRead)
                    if (mutableRead.isNotEmpty()) stringBuilder.appendLine(mutableRead)

                    variables.variables.forEach { (rewrite, varName) ->
                        val prefix = varName[0]
                        if (prefix == 'r') {
                            newChildren.add("@Child private lateinit var $varName: $TERMNODE")
                            val rewriteStr = makeVariable(varName, "insert($rewrite).rewrite(frame)", init = false)
                            stringBuilder.appendLine(rewriteStr)
                        } else if (prefix == 'm') {
                            stringBuilder.appendLine(makeVariable(varName, value = rewrite))
                        }
                    }

                    if (contextualWrite.isNotEmpty()) stringBuilder.appendLine(contextualWrite)

                    val stepVariableSet =
                        variables.variables.entries.filter { it.value[0] == 's' }.map { it.toPair() }.toMutableSet()
                    val pairs = stepVariableSet.map { stepVar ->
                        val (rewriteLhs, rewriteRhs) = stepVar
                        val condition = "$rewriteLhs.isReducible()"
                        val step = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                        val rulesForStepVar = ruleObjs
                            .sortedBy { rule -> rule.rulePriority }
                            .filter { rule -> rule.stepVariable == stepVar }
                        val innerPairs = rulesForStepVar.map { rule ->
                            val conditions = rule.getSortedConditions()
                            if (conditions.isEmpty()) "true" to rule.bodyStr
                            else conditions to rule.bodyStr
                        }
                        val ruleBody = makeWhenStatement(innerPairs, elseBranch = "abort(${strStr(name)})")
                        val controlReadStr = if (controlReads.isEmpty()) "" else controlReads.joinToString("\n")

                        val body = listOf(step, controlReadStr, ruleBody)
                            .filter { it.isNotEmpty() }
                            .joinToString("\n")
                        condition to body
                    }

                    val rulesWithoutStepVar = ruleObjs
                        .sortedBy { rule -> rule.rulePriority }
                        .filter { rule -> rule.stepVariable == null }
                    val withoutPairs = rulesWithoutStepVar.map { rule ->
                        val conditions = rule.getSortedConditions()
                        if (conditions.isEmpty()) "true" to rule.bodyStr
                        else conditions to rule.bodyStr
                    }
                    if (pairs.isEmpty()) makeWhenStatement(withoutPairs, elseBranch = "abort(${strStr(name)})")
                    if (withoutPairs.isEmpty()) makeWhenStatement(pairs, elseBranch = "abort(${strStr(name)})")
                    else makeWhenStatement(withoutPairs + pairs, elseBranch = "abort(${strStr(name)})")
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
