package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.RewriteData
import main.exceptions.DetailedException
import main.objects.EntityObject
import main.objects.Object

data class Condition(val expr: String, val priority: Int = 1)

class FunconObject(
    ctx: FunconDefinitionContext,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val term: ExprContext? = null,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
    registry: Registry,
) : Object(ctx, metaVariables, registry) {

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
            val paramStrs = getParamStrs(registry, pattern)
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
                        val typeCondition = makeTypeCondition(registry, paramStr, argType)
                        addCondition(typeCondition)
                    }

                    is VariableContext -> {
                        val typeCondition = makeTypeCondition(registry, paramStr, metaVariableMap[argType.text]!!)
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

            val sizeCondition = makeSizeCondition(registry, pattern)
            if (sizeCondition != null) {
                val (condition, priority) = sizeCondition
                addCondition(condition)
                rulePriority = priority
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
                        val rewriteLhs = rewrite(registry, pattern, lhs, rewriteData)
                        // The two branches below build the same `rewriteRhs`, which
                        // reads like a duplicate to hoist. It is not one:
                        // `variables.getVar` allocates the next name, so calling it
                        // in the datatype-value branch too would renumber every r0,
                        // r1 in the output. Only the other two branches bind a name.
                        val newRewriteData = if (rhs is FunconExpressionContext && rhs.name.text == "datatype-value") {
                            makeDatatypeValueRewriteData(rhs, rewriteLhs)
                        } else if (rhs is FunconExpressionContext) {
                            val rewriteRhs = makeGetter(variables.getVar(rewriteLhs, "r"), frame = true)
                            getParamStrs(registry, rhs, rewriteRhs) + listOf(makeRewriteDataObject(rhs, rewriteRhs))
                        } else {
                            val rewriteRhs = makeGetter(variables.getVar(rewriteLhs, "r"), frame = true)
                            listOf(makeRewriteDataObject(rhs, rewriteRhs))
                        }
                        rewriteData.addAll(newRewriteData)
                    }

                    is TransitionPremiseWithMutableEntityContext,
                        -> {
                        val rewriteLhs = rewrite(registry, pattern, lhs, rewriteData)
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
                        val rewriteLhs = rewrite(registry, pattern, lhs, rewriteData)
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
                            val exprRewrite = rewrite(registry, pattern, expr, rewriteData)
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
                        val rewriteLhs = rewrite(registry, pattern, lhs, rewriteData)
                        val condition =
                            if (rhs is VariableContext) {
                                val rewriteRhs = rewrite(registry, pattern, rhs, rewriteData)
                                "${rewriteLhs}.isInType($rewriteRhs)"
                            } else if (rhs is ComplementExpressionContext && rhs.operand is VariableContext) {
                                val rewriteRhs = rewrite(registry, pattern, rhs.operand, rewriteData)
                                "!${rewriteLhs}.isInType($rewriteRhs)"
                            } else {
                                makeTypeCondition(registry, rewriteLhs, premise.type)
                            }
                        addCondition(condition, priority = 2)
                    }
                }
            }
        }

        fun labelToObject(label: LabelContext): EntityObject {
            return if (label.name.text == "abrupt") {
                // TODO: Edge case due to bug in CBS code for yield-on-abrupt. Remove when fixed.
                registry.require("abrupted")
            } else {
                getObject(registry, label)
            } as EntityObject
        }

        fun rewriteEntity(label: LabelContext, labelObj: EntityObject): List<RewriteData> {
            return if (label.value is FunconExpressionContext) {
                getParamStrs(registry, label.value, prefix = labelObj.asVarName) + RewriteData(
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
                val rewriteStr = rewrite(registry, pattern, label.value, rewriteData)
                val newRewriteData = makeRewriteDataObject(label.value, rewriteStr)
                rewriteData.add(newRewriteData)
                rewriteStr
            } else "SequenceNode()"
            return labelObj.putStr(valueStr)
        }

        private fun processEntityCondition(label: LabelContext) {
            val labelObj = labelToObject(label)
            /*
              The environment is a scope, not an optional control entity: it always exists and
              is often legitimately empty, so a rule reading it must still apply when nothing is
              in scope. funcons-tools relies on this, since `closure(12)` evaluates to
              `abstraction(closed(scope("...",12)))` with an empty scope rather than getting
              stuck. Guarding on isNotEmpty() left `closure` unusable whenever no identifier was
              bound, and left an empty scope unable to be re-entered.

              Every other entity keeps its presence check: `abrupted`, `given`, `control-signal`
              and `tail` are only present when something put them there, and their rules must not
              fire otherwise.
            */
            if (label.name.text == "environment") return
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
                            val valueStr = if (label.value != null) {
                                rewrite(registry, pattern, label.value, rewriteData)
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
                        val entityRewrite = rewrite(registry, pattern, labelRhs.value, rewriteData)
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

            rewriteStr = rewrite(registry, pattern, term, rewriteData, copy = true)
        }
    }

    /**
     * The pieces a generated `reduce` is assembled from.
     *
     * @param children `@Child` fields for the values the body binds, declared
     * outside `reduce` so a step variable survives across calls.
     * @param prologue the reads of those fields, which have to happen before the
     * `return` rather than in it.
     */
    private class Reduction(val children: List<String>, val prologue: List<String>, val body: String)

    override val contentStr: String
        get() {
            val reduction = reduction()

            val body = StringBuilder()
            reduction.prologue.forEach { line -> body.appendLine(line) }
            body.appendLine("return ${reduction.body}")

            val outerStringBuilder = StringBuilder()
            if (reduction.children.isNotEmpty()) {
                reduction.children.forEach { child -> outerStringBuilder.appendLine(child) }
                outerStringBuilder.appendLine()
            }
            outerStringBuilder.appendLine(makeReduceFunction(body.toString(), TERMNODE))
            return outerStringBuilder.toString()
        }

    /**
     * A funcon reduces in one of two ways, and the second only if it has rules.
     *
     * A funcon written as `funcon F = <term>` *is* that term. Otherwise it has
     * rules, and reduces by matching its argument against them.
     */
    private fun reduction(): Reduction = when {
        term != null -> Reduction(
            emptyList(),
            emptyList(),
            rewrite(registry, ctx, term, copy = true)
        )

        rules.isNotEmpty() -> reductionByRules(makeRuleObjs())

        else -> throw DetailedException("Funcon $name does not have any associated rules.")
    }

    private fun makeRuleObjs(): List<Rule> {
        val ruleObjs = rules.map { rule ->
            val premises = rule.premises()?.premiseExpr()?.toList() ?: emptyList()
            Rule(premises, rule.conclusion, metavariableMap)
        }

        // Two rules whose conditions are equal match exactly the same values, so
        // which one runs would be arbitrary. CBS accepts that, so it is reported.
        if (ruleObjs.map { it.conditions }.toSet().size != ruleObjs.size)
            throw DetailedException("rules for $name are not distinct.")

        return ruleObjs
    }

    /**
     * Reduces by rules: read the bound values, then a `when` over the rules.
     *
     * The `when` is the rules that bind a step variable, followed by the rules
     * that do not, so the rules which reduce the argument further are tried
     * before the ones that would abort on it.
     */
    private fun reductionByRules(ruleObjs: List<Rule>): Reduction {
        // Built while the rules are constructed, so the rules have to be built
        // first even though their code comes later.
        val children = mutableListOf<String>()
        val prologue = mutableListOf<String>()

        if (ctxConclusionRead.isNotEmpty()) prologue.add(ctxConclusionRead)
        if (mutableRead.isNotEmpty()) prologue.add(mutableRead)

        variables.variables.forEach { (rewrite, varName) ->
            val prefix = varName[0]
            if (prefix in listOf('r', 'm')) {
                children.add("@Child private var $varName: $TERMNODE? = null")
                prologue.add(makeRewriteGetter(varName, rewrite))
            }
        }

        val stepPairs = stepPairs(ruleObjs)
        val withoutPairs = refocusPairs(ruleObjs)

        return Reduction(
            children,
            prologue,
            makeWhenStatement(withoutPairs + stepPairs, elseBranch = elseBranch())
        )
    }

    /**
     * One `when` case per step variable.
     *
     * A step variable is a subterm the rules reduce in place, which is how
     * `while` and the folds work: a case guards on `isReducible`, reduces the
     * subterm once, and matches the same variable again on the next call.
     */
    private fun stepPairs(ruleObjs: List<Rule>): List<Pair<String, String>> {
        val stepVariableSet = variables.variables.entries
            .filter { it.value[0] == 's' }
            .map { it.toPair() }
            .toMutableSet()

        return stepVariableSet.map { stepVar ->
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
                makeWhenStatement(innerPairs, elseBranch = "abort()")
            }

            val body = listOf(
                contextualWrite,
                step,
                controlReads.joinToString("\n"),
                ruleBody
            )
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            condition to body
        }
    }

    /**
     * One `when` case per rule that does not reduce a subterm, in the order CBS
     * declares them, each guarded by its conditions.
     */
    private fun refocusPairs(ruleObjs: List<Rule>): List<Pair<String, String>> {
        val pairs = ruleObjs
            .sortedBy { rule -> rule.rulePriority }
            .filter { rule -> rule.stepVariable == null }
            .map { rule ->
                val conditions = rule.getSortedConditions()
                if (conditions.isEmpty()) "true" to rule.bodyStr
                else conditions to rule.bodyStr
            }

        /*
          funcons-tools matches with an optRefocus: when both sides are
          abstractions it decomposes each into its unevaluated body and retries.
          That is what the datatype rule of match would do if `abstraction` were
          a datatype value, which funcons-intgen arranges by promoting the
          body-less `abstractions` Type to a DataTypeDecl. We cannot make the
          hand-written AbstractionNode a DatatypeValuesNode without also making
          it ground, so the branch is emitted directly instead, after the
          datatype and map rules and before the is-equal fallback. Both sides
          must be abstractions: without that, `match(12, abstraction(x))` would
          succeed where the reference fails.
        */
        val refocusPairs = when (name) {
            "match", "match-loosely" -> listOf(
                "get(0) is AbstractionNode && get(1) is AbstractionNode" to
                    "${toNodeName(name)}(get(0).getCopy(0), get(1).getCopy(0))"
            )
            else -> emptyList()
        }

        if (refocusPairs.isEmpty() || pairs.size < 2) return pairs
        val last = pairs.size - 1
        return pairs.subList(0, last) + refocusPairs + pairs.subList(last, pairs.size)
    }

    /*
      is-equal is total in the reference: `true` exactly when both arguments are
      ground values and equal, `false` otherwise, never stuck. Closures are the
      case that needs this, since a closure is not a value at all, so neither the
      `~ground-values` rules nor the `isInValues` guards can match and Fun v2
      would otherwise abort. The reference answers false for every closure:
      closure(12) vs closure(12), closure(12) vs 1, and closure(abstraction 12)
      vs itself.
    */
    private fun elseBranch(): String = if (name == "is-equal") "FalseNode()" else "abort()"
}

