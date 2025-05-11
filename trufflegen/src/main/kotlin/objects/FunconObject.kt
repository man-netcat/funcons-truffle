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
    val term: ExprContext? = null,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : AbstractFunconObject(ctx, metaVariables) {

    inner class VariableGenerator() {
        val prefixIndices = mutableMapOf<String, Int>()
        val variables = mutableMapOf<String, String>()

        private fun newVar(prefix: String): String {
            val index = prefixIndices.getOrDefault(prefix, 0)
            prefixIndices[prefix] = index + 1
            return "$prefix$index"
        }

        fun getVar(rewrite: String, prefix: String): String {
            return variables.getOrPut(rewrite) { newVar(prefix) }
        }
    }

    val variables = VariableGenerator()

    var contextualWrite = ""
    var ctxConclusionRead = ""
    var ctxConclusionWrite = ""
    var mutableRead = ""
    val controlReads = mutableSetOf<String>()

    inner class Rule(
        val premises: List<PremiseExprContext>,
        val conclusion: PremiseExprContext,
        val metaVariableMap: Map<String, ExprContext>,
    ) {
        var mutableWrite = ""
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

        private fun patternConditions(pattern: FunconExpressionContext) {
            val paramStrs = getParamStrs(pattern)
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

            val sizeCondition = makeSizeCondition(pattern)
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

        private fun processPremises(pattern: ExprContext) {
            premises.forEach { premise ->
                val (lhs, rhs) = extractLhsRhs(premise)

                when (premise) {
                    is RewritePremiseContext -> {
                        val rewriteLhs = rewrite(pattern, lhs, rewriteData)
                        val newRewriteData = if (rhs is FunconExpressionContext && rhs.name.text == "datatype-value") {
                            val (i, v) = (rhs.args() as MultipleArgsContext).exprs().expr()
                            i as TypeExpressionContext
                            v as TypeExpressionContext
                            listOf(
                                RewriteData(i.value, i.type, "$rewriteLhs.id"),
                                RewriteData(v.value, v.type, "$rewriteLhs.args")
                            )
                        } else if (rhs is FunconExpressionContext) {
                            val rewriteRhs = makeGetter(variables.getVar(rewriteLhs, "r"), frame = true)
                            getParamStrs(rhs, rewriteRhs) + listOf(makeRewriteDataObject(rhs, rewriteRhs))
                        } else {
                            val rewriteRhs = makeGetter(variables.getVar(rewriteLhs, "r"), frame = true)
                            listOf(makeRewriteDataObject(rhs, rewriteRhs))
                        }
                        rewriteData.addAll(newRewriteData)
                    }

                    is TransitionPremiseWithMutableEntityContext,
                        -> {
                        val rewriteLhs = rewrite(pattern, lhs, rewriteData)
                        val rewriteRhs = makeGetter(variables.getVar(rewriteLhs, "m"), frame = true)
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
                        val rewriteLhs = rewrite(pattern, lhs, rewriteData)
                        val rewriteRhs = variables.getVar(rewriteLhs, "s")
                        val newRewriteData = makeRewriteDataObject(rhs, rewriteRhs)
                        rewriteData.add(newRewriteData)

                        when (premise) {
                            is TransitionPremiseWithContextualEntityContext -> {
                                val label = premise.context_
                                val labelObj = labelToObject(label)
                                contextualWrite = makeEntityAssignment(pattern, label, labelObj)
                            }

                            is TransitionPremiseWithControlEntityContext -> {
                                val labels = getControlEntityLabels(premise)
                                labels.forEach { (label, labelObj) ->
                                    processEntityCondition(label)
                                    val controlRead = makeVariable(labelObj.asVarName, labelObj.getStr())
                                    controlReads.add(controlRead)
                                }
                            }
                        }

                        stepVariable = rewriteLhs to rewriteRhs
                    }

                    is BooleanPremiseContext -> {
                        fun getBooleanNode(text: String): String =
                            if (text == "true") "ValueTrueNode" else "ValueFalseNode"

                        fun bindVariable(expr: ExprContext): String {
                            val exprRewrite = rewrite(pattern, expr, rewriteData)
                            if (expr.text in params.filter { !it.type.computes }.map { it.value }) return exprRewrite
                            val variable = makeGetter(variables.getVar(exprRewrite, "r"), frame = true)
                            val rhsRewriteData = makeRewriteDataObject(expr, variable)
                            rewriteData.add(rhsRewriteData)
                            return variable
                        }

                        val condition = if (lhs.text in listOf("true", "false")) {
                            val rhsVar = bindVariable(rhs)
                            val boolean = getBooleanNode(lhs.text)
                            "$rhsVar is $boolean"
                        } else if (rhs.text in listOf("true", "false")) {
                            val lhsVar = bindVariable(lhs)
                            val boolean = getBooleanNode(rhs.text)
                            "$lhsVar is $boolean"
                        } else if (lhs.text == "()") {
                            val rhsVar = bindVariable(rhs)
                            "$rhsVar.isEmpty()"
                        } else if (rhs.text == "()") {
                            val lhsVar = bindVariable(lhs)
                            "$lhsVar.isEmpty()"
                        } else {
                            val lhsVar = bindVariable(lhs)
                            val rhsVar = bindVariable(rhs)

                            val op = when (premise.op.text) {
                                "==" -> "=="
                                "=/=" -> "!="
                                else -> throw DetailedException("Unexpected operator type: ${premise.op.text}")
                            }
                            "$lhsVar $op $rhsVar"
                        }

                        addCondition(condition, priority = 2)
                    }

                    is TypePremiseContext -> {
                        val rewriteLhs = rewrite(pattern, lhs, rewriteData)
                        val condition =
                            if (rhs is VariableContext) {
                                val rewriteRhs = rewrite(pattern, rhs, rewriteData)
                                "${rewriteLhs}.isInType($rewriteRhs)"
                            } else if (rhs is ComplementExpressionContext && rhs.operand is VariableContext) {
                                val rewriteRhs = rewrite(pattern, rhs.operand, rewriteData)
                                "!${rewriteLhs}.isInType($rewriteRhs)"
                            } else {
                                makeTypeCondition(rewriteLhs, premise.type)
                            }
                        addCondition(condition, priority = 2)
                    }
                }
            }
        }

        fun labelToObject(label: LabelContext): EntityObject {
            return if (label.name.text == "abrupt") {
                // TODO: Edge case due to bug in CBS code for yield-on-abrupt. Remove when fixed.
                globalObjects["abrupted"]
            } else {
                getObject(label)
            } as EntityObject
        }

        fun rewriteEntity(label: LabelContext, labelObj: EntityObject): List<RewriteData> {
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
            pattern: ExprContext,
            label: LabelContext,
            labelObj: EntityObject,
        ): String {
            val valueStr = if (label.value != null && label.value.text != "_?") {
                val rewriteStr = rewrite(pattern, label.value, rewriteData)
                val newRewriteData = makeRewriteDataObject(label.value, rewriteStr)
                rewriteData.add(newRewriteData)
                rewriteStr
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

        private fun processConclusion(pattern: ExprContext) {
            when (conclusion) {
                is TransitionPremiseWithContextualEntityContext -> {
                    val label = conclusion.context_
                    val labelObj = labelToObject(label)
                    processEntityCondition(label)
                    ctxConclusionRead = makeVariable(labelObj.asVarName, labelObj.getStr())
                    ctxConclusionWrite = labelObj.putStr(labelObj.asVarName)
                }

                is TransitionPremiseWithControlEntityContext -> {
                    val steps = conclusion.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                    steps.forEach { step ->
                        step.labels().label().forEach { label ->
                            val labelObj = labelToObject(label)
                            val valueStr = if (label.value != null && label.value.text != "_?") {
                                rewrite(pattern, label.value, rewriteData)
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
                        val entityRewrite = rewrite(pattern, labelRhs.value, rewriteData)
                        val rewrite = if (labelRhs.value is FunconExpressionContext) {
                            val entityVariable = makeGetter(variables.getVar(entityRewrite, "r"), frame = true)
                            val newNewRewriteData = makeRewriteDataObject(labelRhs.value, entityVariable)
                            rewriteData.add(newNewRewriteData)
                            entityVariable
                        } else entityRewrite
                        mutableWrite = labelRhsObj.putStr(rewrite)
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

        private fun processEntities() {
            fun makeEntityData(premiseExpr: PremiseExprContext, isPremise: Boolean, emptyPremises: Boolean) {
                val labels = when {
                    premiseExpr is TransitionPremiseWithContextualEntityContext && !isPremise ->
                        listOf(premiseExpr.context_ to labelToObject(premiseExpr.context_))

                    premiseExpr is TransitionPremiseWithControlEntityContext && isPremise && !emptyPremises ->
                        getControlEntityLabels(premiseExpr)

                    premiseExpr is TransitionPremiseWithMutableEntityContext ->
                        listOf(premiseExpr.entityLhs to labelToObject(premiseExpr.entityLhs))

                    else -> emptyList()
                }
                val newRewriteData = labels.flatMap { (label, labelObj) -> rewriteEntity(label, labelObj) }
                rewriteData.addAll(newRewriteData)
            }

            fun isTransitionPremise(premiseExpr: PremiseExprContext): Boolean = when (premiseExpr) {
                is TransitionPremiseContext,
                is TransitionPremiseWithMutableEntityContext,
                is TransitionPremiseWithControlEntityContext,
                is TransitionPremiseWithContextualEntityContext,
                    -> true

                else -> false
            }
            premises.forEach { premise -> makeEntityData(premise, isPremise = true, emptyPremises = false) }
            makeEntityData(conclusion, isPremise = false, emptyPremises = !premises.any(::isTransitionPremise))
        }

        init {
            val (pattern, term) = extractLhsRhs(conclusion)
            pattern as FunconExpressionContext // We assume a pattern is always a funcon expression

            // Add values for entities
            processEntities()

            // Add rewrite data for premises
            processPremises(pattern)

            // Build rewrites from conclusions
            processConclusion(pattern)

            // Add pattern matching conditions
            patternConditions(pattern)

            rewriteStr = rewrite(pattern, term, rewriteData, copy = true)
        }
    }

    override val contentStr: String
        get() {
            val newChildren = mutableListOf<String>()
            val stringBuilder = StringBuilder()

            val returnStr = "return " + when {
                term != null -> rewrite(ctx, term, copy = true)
                rules.isNotEmpty() -> {
                    val ruleObjs = rules.map { rule ->
                        val premises = rule.premises()?.premiseExpr()?.toList() ?: emptyList()
                        Rule(premises, rule.conclusion, metavariableMap)
                    }

                    if (ruleObjs.map { it.conditions }.toSet().size != ruleObjs.size) {
                        throw DetailedException("rules for $name are not distinct.")
                    }

                    if (ctxConclusionRead.isNotEmpty()) stringBuilder.appendLine(ctxConclusionRead)
                    if (mutableRead.isNotEmpty()) stringBuilder.appendLine(mutableRead)

                    variables.variables.forEach { (rewrite, varName) ->
                        val prefix = varName[0]
                        if (prefix in listOf('r', 'm')) {
                            newChildren.add("@Child private var $varName: $TERMNODE? = null")
                            stringBuilder.appendLine(makeRewriteGetter(varName, rewrite))
                        }
                    }

                    val stepVariableSet =
                        variables.variables.entries.filter { it.value[0] == 's' }.map { it.toPair() }.toMutableSet()
                    val stepPairs = stepVariableSet.map { stepVar ->
                        val (rewriteLhs, rewriteRhs) = stepVar
                        val condition = "$rewriteLhs.isReducible()"
                        val step = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)")
                        val rulesForStepVar = ruleObjs
                            .sortedBy { rule -> rule.rulePriority }
                            .filter { rule -> rule.stepVariable == stepVar }

                        val ruleBody = if (rulesForStepVar.size == 1 && rulesForStepVar[0].conditions.isEmpty()) {
                            rulesForStepVar[0].bodyStr
                        } else {
                            val innerPairs = rulesForStepVar.map { rule ->
                                val conditions = rule.getSortedConditions()
                                val bodyStr = listOf(
                                    ctxConclusionWrite,
                                    rule.bodyStr
                                ).filter { it.isNotEmpty() }
                                    .joinToString("\n")
                                if (conditions.isEmpty()) "true" to bodyStr
                                else conditions to bodyStr
                            }
                            makeWhenStatement(innerPairs, elseBranch = "abort(${strStr(name)})")
                        }


                        val body =
                            listOf(
                                contextualWrite,
                                step,
                                controlReads.joinToString("\n"),
                                ruleBody
                            )
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
                    makeWhenStatement(withoutPairs + stepPairs, elseBranch = "abort(${strStr(name)})")
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
    override val superClassStr: String get() = makeFunCall(TERMNODE)
    override val keyWords: List<String> = emptyList()

    override val contentStr: String
        get() {
            val reduceBuilder = StringBuilder()
            val paramStr = params.map { param -> "p${param.index}" }
            val cache = makeFunCall(
                "ValueNodeFactory.datatypeValueNode",
                listOf(strStr(name), "SequenceNode(${paramStr.joinToString()})")
            )
            val ctor = makeFunCall("Value$nodeName", paramStr)
            val returnStr = "return $cache { $ctor }"

            reduceBuilder.appendLine(returnStr)
            return makeReduceFunction(reduceBuilder.toString(), TERMNODE)
        }
}
