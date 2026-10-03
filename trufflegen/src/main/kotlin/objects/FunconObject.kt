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
        val steppedGetters = mutableMapOf<String, String>()

        private fun newVar(prefix: String): String {
            val index = prefixIndices.getOrDefault(prefix, 0)
            prefixIndices[prefix] = index + 1
            return "$prefix$index"
        }

        fun getVar(rewrite: String, prefix: String): String {
            return variables.getOrPut(rewrite) { newVar(prefix) }
        }

        /*
          The second half of a transition premise: a name of its own for the
          stepped result, keyed so that it cannot collide with the matched term it
          steps. It goes in `variables` like any other so that declaration order
          is preserved -- `getM1` names `getT0`, so `getT0` has to be emitted
          first.
         */
        fun steppedVar(matchedVar: String): String {
            val name = newVar("t")
            variables["stepped:$name"] = name
            steppedGetters[name] = makeGetter(matchedVar, frame = true)
            return name
        }
    }

    val variables = VariableGenerator()

    var ctxConclusionRead = ""
    var ctxConclusionWrite = ""
    var mutableRead = ""
    val controlReads = mutableSetOf<String>()
    /*
      The variable names of the control entities read in a body, as opposed to the
      contextual entities written there. A premise or condition naming one of these
      is compiled inside the body, after the step, because a step may be what sets
      the entity; naming one in the prologue would see the wrong value.
    */
    val controlReadVars = mutableSetOf<String>()

    inner class Rule(
        val premises: List<PremiseExprContext>,
        val conclusion: PremiseExprContext,
        val metaVariableMap: Map<String, ExprContext>,
    ) {
        /*
          One rule per contextual premise. A funcon with two rules reading the same
          contextual entity differently (`give` has one, `postpone` has two) would
          otherwise have the last rule's write applied to all of them.
        */
        var contextualWrite = ""
        /*
          A rule may write more than one mutable entity: `thread-activate` writes both
          `thread-map` and `active-thread-set`. These were collected into a single
          string that each write overwrote, so only the last one was stored and
          `thread-map` was silently left unset. Every rule in the stable corpus writes
          at most one, which is why this never showed up there.
         */
        val mutableWrites = mutableListOf<String>()
        val extraMutableReads = linkedMapOf<String, String>()
        val mutableReadVars = mutableSetOf<String>()
        val controlWrites = mutableSetOf<String>()
        var stepVariable: Pair<String, String>? = null

        /*
          The entity labels whose presence condition this rule could add but has not,
          kept so that `makeRuleObjs` can ask for them if it turns out this funcon's
          rules cannot otherwise be told apart. `store(Sigma)` in `allocate-variable`
          is the case that makes this necessary: it is read as the state the rule
          starts from, so guarding on `store.isNotEmpty()` would stop it working on
          an initialised and therefore empty store, and it is only ever correct to
          guard when something needs distinguishing.
        */
        private val deferredEntityLabels = mutableListOf<LabelContext>()

        /*
          The condition alone is not enough: it names the entity as a variable, and the
          prologue has to declare it. `thread-atomic`'s conditions read `abrupted`, which
          nothing else in that rule reads, and the generated case named a variable that
          did not exist.
        */
        internal fun addEntityConditions() = deferredEntityLabels.forEach { label ->
            val labelObj = labelToObject(label)
            extraMutableReads.putIfAbsent(
                labelObj.asVarName,
                makeVariable(labelObj.asVarName, labelObj.getStr())
            )
            processEntityCondition(label)
        }

        val conditions = mutableSetOf<Condition>()
        private val rewriteStr: String
        var rulePriority = 1

        val rewriteData: MutableList<RewriteData> = mutableListOf()

        val bodyStr: String
            get() = (controlWrites + mutableWrites + listOf(rewriteStr)).filter { it.isNotEmpty() }
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

        private fun getControlEntityLabels(premiseExpr: TransitionPremiseWithControlEntityContext): List<Pair<LabelContext, EntityObject>> =
            getControlEntityLabels(premiseExpr.steps())

        private fun getControlEntityLabels(steps: StepsContext): List<Pair<LabelContext, EntityObject>> {
            return steps.step().sortedBy { it.sequenceNumber.text.toInt() }
                .flatMap { step ->
                    step.labels().label().map { label -> label to labelToObject(label) }
                }
        }

        /*
          The entities a step names, in step order. `thread-atomic` reads its abruptness
          this way, and a step can name as many as the transition carries, so all of them
          are read and not just the first.
        */
        private fun stepLabels(premiseExpr: TransitionPremiseWithMutableEntityContext):
                List<Pair<LabelContext, EntityObject>> =
            premiseExpr.steps()?.step()
                ?.sortedBy { it.sequenceNumber.text.toInt() }
                ?.flatMap { step ->
                    step.labels().label().map { label -> label to labelToObject(label) }
                }
                ?: emptyList()

        private fun processPremises(pattern: ExprContext) {
            premises.forEach { premise ->
                val (lhs, rhs) = extractLhsRhs(premise)

                when (premise) {
                    is RewritePremiseContext -> {
                        val rewriteLhs = rewrite(registry, pattern, lhs, rewriteData)
                        /*
                          A rewrite premise can be over a control entity's value,
                          as `A ~> abstraction Y` is when `A` was bound by
                          `X --postponing(A)-> X'`. Its lhs is rewritten inline
                          rather than through a getter: a getter is declared in
                          the prologue, before the step that sets the entity, and
                          reading the entity there would see the value the step
                          left rather than the one the premise matched. The
                          control read in the body holds the matched value, and
                          is in scope at the point the conclusion is built.
                        */
                        fun rewriteRhsStr(): String =
                            if (controlReadVars.contains(rewriteLhs)) {
                                "$rewriteLhs.rewrite(frame)"
                            } else {
                                makeGetter(variables.getVar(rewriteLhs, "r"), frame = true)
                            }

                        // The two branches below build the same `rewriteRhs`, which
                        // reads like a duplicate to hoist. It is not one:
                        // `variables.getVar` allocates the next name, so calling it
                        // in the datatype-value branch too would renumber every r0,
                        // r1 in the output. Only the other two branches bind a name.
                        val newRewriteData = if (rhs is FunconExpressionContext && rhs.name.text == "datatype-value") {
                            makeDatatypeValueRewriteData(rhs, rewriteLhs)
                        } else if (rhs is FunconExpressionContext) {
                            val rewriteRhs = rewriteRhsStr()
                            getParamStrs(registry, rhs, rewriteRhs) + listOf(makeRewriteDataObject(rhs, rewriteRhs))
                        } else {
                            val rewriteRhs = rewriteRhsStr()
                            listOf(makeRewriteDataObject(rhs, rewriteRhs))
                        }
                        rewriteData.addAll(newRewriteData)
                    }

                    is TransitionPremiseWithMutableEntityContext,
                        -> {
                        val rewriteLhs = rewrite(registry, pattern, lhs, rewriteData)
                        /*
                          A transition premise is one step, not a rewrite: `thread-step`
                          is `< X, thread-stepping(TI), thread-map(TM) > ---> < X', ... >`
                          over the body `X` of the thread it is stepping, and the whole
                          point of a step is that the scheduler decides when the next one
                          happens. Rewriting `X` here ran the body to normal form inside
                          a single `thread-step`, and left nothing for the rule to guard
                          on: with `X` already a value, the rule that steps it still
                          matched, so a terminated thread was stepped forever instead of
                          being removed. The left side is therefore bound uneagered and
                          guarded on `isReducible`, and the right side to its one step.

                          The rules that ask whether the premise's term is *already* a
                          value still read the left side, which is what
                          `thread-step`'s two termination rules do with `X ~> (V:values)`.
                         */
                        val matchedVar = variables.getVar(rewriteLhs, "m")
                        val matchedGetter = makeGetter(matchedVar, frame = true)
                        val steppedVar = variables.steppedVar(matchedVar)
                        val rewriteRhs = makeGetter(steppedVar, frame = true)
                        val newRewriteData = makeRewriteDataObject(rhs, rewriteRhs)
                        rewriteData.add(newRewriteData)
                        addCondition("$matchedGetter.isReducible()")

                        /*
                          A mutable-entity premise can name more than one entity,
                          as `thread-activate` does over `thread-map` and
                          `active-thread-set`. The first binds the entity's value,
                          which is the one the term refers to.
                          A premise that reads without writing, `< X, E > ---> X'`,
                          has no entity on its right and binds nothing.

                          No presence condition is added here. This is a real premise
                          of a rule with others, and the entity is read as the state
                          the rule starts from: `allocate-variable` works from an
                          initialised, and therefore empty, store, so requiring
                          `store.isNotEmpty()` would change what it does. The guard
                          belongs where a rule has no premises of its own, which is
                          `processEntities`.
                        */

/*
                          A mutable-entity premise can write more than one entity, as
                          `thread-step`'s second premise does over `thread-stepping` and
                          `thread-map`. Each entity named on its right holds whatever
                          the premise left it holding, so each of its labels binds to
                          that entity. `TM'` is what makes this necessary: the premise
                          after it builds the new thread-map from `TM'`, and with only
                          the first label bound that name was never given a value.

                          The entity is re-read where the name is used, rather than
                          bound to the `val` the prologue declares for it. That `val`
                          is read once, on entry to `reduceRules`, and the step this
                          premise takes runs after that: a thread body can activate
                          further threads, so by the time `thread-step` writes the
                          thread-map the entry value is stale and the new threads are
                          gone from it. `active-thread-set` keeps them, so the map and
                          the set disagree, `update-thread-stepping` picks a thread
                          that `lookup` cannot find, and every rule of `thread-step`
                          misses. That is the whole of the 13 failures in the unstable
                          corpus's scheduling tests.

                          The read is fresh but not hoisted, so it happens where it is
                          written, which is after the step: `map-override({TI |-> ...},
                          TM')` evaluates its first argument -- and so the step -- before
                          it reads `TM'`.
                         */
                        val labelRhs = premise.entityRhs
                        labelRhs?.label()?.forEach { label ->
                            val labelObj = labelToObject(label)
                            val labelValues = label.value?.expr() ?: emptyList()
                            if (labelValues.size == 1) {
                                val newNewRewriteData =
                                    makeRewriteDataObject(labelValues[0], labelObj.getStr())
                                rewriteData.add(newNewRewriteData)
                            }
                        }
                    }

                    is TransitionPremiseContext,
                    is TransitionPremiseWithContextualEntityContext,
                    is TransitionPremiseWithContextualEntityAndStepsContext,
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

                            is TransitionPremiseWithContextualEntityAndStepsContext -> {
                                val label = premise.context_
                                val labelObj = labelToObject(label)
                                contextualWrite = makeEntityAssignment(pattern, label, labelObj)

                                val labels = getControlEntityLabels(premise.steps())
                                labels.forEach { (label, labelObj) ->
                                    processEntityCondition(label)
                                    val controlRead = makeVariable(labelObj.asVarName, labelObj.getStr())
                                    controlReads.add(controlRead)
                                    controlReadVars.add(labelObj.asVarName)
                                }
                            }

                            is TransitionPremiseWithControlEntityContext -> {
                                val labels = getControlEntityLabels(premise)
                                labels.forEach { (label, labelObj) ->
                                    processEntityCondition(label)
                                    val controlRead = makeVariable(labelObj.asVarName, labelObj.getStr())
                                    controlReads.add(controlRead)
                                    controlReadVars.add(labelObj.asVarName)
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
            val exprs = label.value?.expr() ?: emptyList()
            return when (exprs.size) {
                0 -> emptyList()
                1 -> {
                    val value = exprs[0]
                    if (value is FunconExpressionContext) {
                        getParamStrs(registry, value, prefix = labelObj.asVarName) + RewriteData(
                            null,
                            value,
                            labelObj.asVarName
                        )
                    } else {
                        listOf(makeRewriteDataObject(value, labelObj.asVarName))
                    }
                }
                else -> throw DetailedException("multi-value label ${label.text} is unsupported in a read position")
            }
        }

        private fun makeEntityAssignment(
            pattern: ExprContext,
            label: LabelContext,
            labelObj: EntityObject,
        ): String {
            val exprs = label.value?.expr() ?: emptyList()
            val noValue = exprs.isEmpty() || (exprs.size == 1 && exprs[0].text == "_?")
            val valueStr = if (!noValue) {
                val rewriteStr = if (exprs.size == 1) {
                    rewrite(registry, pattern, exprs[0], rewriteData)
                } else {
                    "SequenceNode(" + exprs.joinToString { rewrite(registry, pattern, it, rewriteData) } + ")"
                }
                if (exprs.size == 1) {
                    val newRewriteData = makeRewriteDataObject(exprs[0], rewriteStr)
                    rewriteData.add(newRewriteData)
                }
                rewriteStr
            } else "SequenceNode()"
            return labelObj.putStr(valueStr)
        }

        /**
         * True when [expr] is a single starred variable, with any type annotation
         * stripped, as in `V*` or `V*:ground-values*`.
         */
        private fun bindsWholeEntity(expr: ExprContext): Boolean =
            expr.text.substringBefore(':').trim().endsWith("*")

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
            val exprs = label.value?.expr() ?: emptyList()
            if (exprs.isEmpty()) {
                val emptyCondition = "${labelObj.asVarName}.isEmpty()"
                rulePriority = 0
                addCondition(emptyCondition, 0)
            } else if (exprs.size == 1 && exprs[0].text in listOf("_", "_?")) {
                // A wildcard read needs no presence check.
            } else if (exprs.size == 1 && bindsWholeEntity(exprs[0])) {
                /*
                  A starred variable binds the entity's whole content, which may be the
                  empty sequence, so `value-index(V*)` matches an index that has not been
                  given anything yet. Requiring `isNotEmpty()` made `allocate-index` fail
                  on the empty index that `initialise-index` has just set up, which left
                  `value-index.config` stuck on the first `give`. A variable without a
                  `*` binds one element and does still need the presence check.
                */
            } else {
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

                is TransitionPremiseWithContextualEntityAndStepsContext -> {
                    val label = conclusion.context_
                    val labelObj = labelToObject(label)
                    processEntityCondition(label)
                    ctxConclusionRead = makeVariable(labelObj.asVarName, labelObj.getStr())
                    ctxConclusionWrite = labelObj.putStr(labelObj.asVarName)

                    val steps = conclusion.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                    steps.forEach { step ->
                        step.labels().label().forEach { stepLabel ->
                            val stepLabelObj = labelToObject(stepLabel)
                            val exprs = stepLabel.value?.expr() ?: emptyList()
                            val valueStr = if (exprs.isEmpty()) {
                                "SequenceNode()"
                            } else if (exprs.size == 1) {
                                rewrite(registry, pattern, exprs[0], rewriteData)
                            } else {
                                "SequenceNode(" + exprs.joinToString { rewrite(registry, pattern, it, rewriteData) } + ")"
                            }
                            val controlWrite = stepLabelObj.putStr(valueStr)
                            controlWrites.add(controlWrite)
                        }
                    }
                }

                is TransitionPremiseWithControlEntityContext -> {
                    val steps = conclusion.steps().step().sortedBy { it.sequenceNumber.text.toInt() }
                    steps.forEach { step ->
                        step.labels().label().forEach { label ->
                            val labelObj = labelToObject(label)
                            val exprs = label.value?.expr() ?: emptyList()
                            val valueStr = if (exprs.isEmpty()) {
                                "SequenceNode()"
                            } else if (exprs.size == 1) {
                                rewrite(registry, pattern, exprs[0], rewriteData)
                            } else {
                                "SequenceNode(" + exprs.joinToString { rewrite(registry, pattern, it, rewriteData) } + ")"
                            }
                            val controlWrite = labelObj.putStr(valueStr)
                            controlWrites.add(controlWrite)
                        }
                    }
                }

                is TransitionPremiseWithMutableEntityContext -> {
                    /*
                      The entities of a mutable-entity conclusion pair up by position
                      with those of its premise, as in `thread-activate` over
                      `thread-map` and `active-thread-set`. The first is the entity the
                      term itself reads, so it gets the variable; the rest are plain
                      writes.
                    */
                    val labelLhsList = conclusion.entityLhs?.label()
                    val labelRhsList = conclusion.entityRhs?.label()

                    // Every entity named on the left is read, not just the first: `thread-activate`
                    // matches `thread-map` and `active-thread-set` and writes both, and
                    // the second was left undeclared, so the code that read it named a
                    // variable that did not exist. Keyed by variable name, because the
                    // rules of one funcon read overlapping sets of entities. A funcon
                    // with a single mutable entity per rule, which is every one in the
                    // stable corpus, reads the same one declaration as before.
                    labelLhsList?.forEach { labelLhs ->
                        val labelLhsObj = labelToObject(labelLhs)
                        extraMutableReads.putIfAbsent(
                            labelLhsObj.asVarName,
                            makeVariable(labelLhsObj.asVarName, labelLhsObj.getStr())
                        )
                    }

                    // The entities on the right are written. They pair up by position
                    // with those on the left where both are named, and stand alone where
                    // the conclusion only writes.
                    labelRhsList?.forEachIndexed { index, labelRhs ->
                        val labelLhsAt = labelLhsList?.getOrNull(index)
                        if (labelLhsAt != null && labelRhs.text == labelLhsAt.text) return@forEachIndexed
                        val labelRhsObj = labelToObject(labelRhs)
                        val exprs = labelRhs.value?.expr() ?: emptyList()
                        val mutableWriteStr = when {
                            exprs.isEmpty() -> "SequenceNode()"

                            exprs.size == 1 -> {
                                val entityRewrite = rewrite(registry, pattern, exprs[0], rewriteData)
                                if (exprs[0] is FunconExpressionContext) {
                                    val entityVariable =
                                        makeGetter(variables.getVar(entityRewrite, "r"), frame = true)
                                    val newNewRewriteData = makeRewriteDataObject(exprs[0], entityVariable)
                                    rewriteData.add(newNewRewriteData)
                                    entityVariable
                                } else entityRewrite
                            }

                            else -> {
                                // value-index(V*, V): the stored sequence becomes V* with V appended.
                                // SequenceNode flattens nested sequences, so writing
                                // SequenceNode(valueIndex, get(0)) yields exactly that.
                                "SequenceNode(" +
                                    exprs.joinToString { rewrite(registry, pattern, it, rewriteData) } +
                                    ")"
                            }
                        }
                        mutableWrites.add(labelRhsObj.putStr(mutableWriteStr))
                    }
                }

                is TransitionPremiseWithMutableEntityWriteOnlyContext -> {
                    /*
                      `initialise-thread-map ---> < null-value, thread-map(map()) >`
                      names only the entity it writes: there is nothing on the left to
                      match, so the rule has no presence condition, reads no old value,
                      and simply stores what the right-hand side computes whenever it is
                      reached. This case was unhandled, so every such funcon reduced to
                      `abort()`, which left `initialise-multithreading` a sequence of
                      no-ops and every `multithread` term stuck on the `check-true` of
                      its deadlock guard.

                      Unlike the read-and-write case, a single funcon expression is
                      rewritten directly rather than bound to a getter: there is no
                      matched variable for it to stand for.
                     */
                    conclusion.entityRhs.label().forEach { labelRhs ->
                        val labelRhsObj = labelToObject(labelRhs)
                        val exprs = labelRhs.value?.expr() ?: emptyList()
                        val mutableWriteStr = when {
                            exprs.isEmpty() -> "SequenceNode()"

                            exprs.size == 1 -> rewrite(registry, pattern, exprs[0], rewriteData)

                            else -> {
                                "SequenceNode(" +
                                    exprs.joinToString { rewrite(registry, pattern, it, rewriteData) } +
                                    ")"
                            }
                        }
                        mutableWrites.add(labelRhsObj.putStr(mutableWriteStr))
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
                is TransitionPremiseWithContextualEntityAndStepsContext -> premiseExpr.lhs to premiseExpr.rhs
                is TransitionPremiseWithControlEntityContext -> premiseExpr.lhs to premiseExpr.rhs
                is TransitionPremiseWithMutableEntityContext -> premiseExpr.lhs to premiseExpr.rhs
                is TransitionPremiseWithMutableEntityWriteOnlyContext ->
                    premiseExpr.lhs to premiseExpr.rhs
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

                    premiseExpr is TransitionPremiseWithContextualEntityAndStepsContext && !isPremise ->
                        listOf(premiseExpr.context_ to labelToObject(premiseExpr.context_))

                    premiseExpr is TransitionPremiseWithControlEntityContext && isPremise && !emptyPremises ->
                        getControlEntityLabels(premiseExpr)

                    premiseExpr is TransitionPremiseWithContextualEntityAndStepsContext && isPremise && !emptyPremises ->
                        getControlEntityLabels(premiseExpr.steps())

                    premiseExpr is TransitionPremiseWithMutableEntityContext ->
                        (premiseExpr.entityLhs?.label()?.map { it to labelToObject(it) }
                            ?: emptyList()) + stepLabels(premiseExpr).also {
                            // Kept rather than used, unless this funcon needs them.
                            premiseExpr.entityLhs?.label()?.forEach { deferredEntityLabels.add(it) }
                        }

                    premiseExpr is TransitionPremiseWithMutableEntityWriteOnlyContext -> emptyList()

                    else -> emptyList()
                }

                /*
                  A rule whose body is the mutable-entity premise and nothing else is
                  a bare match on what the entities currently hold, and its rules are
                  told apart by that. `current-thread` has two, reading the same
                  `current-thread` term and differing only in whether `thread-stepping`
                  is empty, so without a presence check on the entity neither is
                  distinguishable from the other.

                  The test is `premises.isEmpty()`, not `emptyPremises`. A rule may
                  have premises that are all boolean or type premises and still read
                  the entity as the state it starts from: `recycle-variables` and
                  `initialise-variable` are guarded by `is-in-set`, yet both have to
                  work on an empty store, and `allocate-variable` has to work from an
                  initialised one, so requiring `store.isNotEmpty()` would change what
                  all three do.
                */
                if (premises.isEmpty() && premiseExpr is TransitionPremiseWithMutableEntityContext) {
                    premiseExpr.entityLhs?.label()?.forEach { processEntityCondition(it) }
                }

                /*
                  A step that names entities, as `--abrupted(A)->` does, states what the
                  transition carries. `thread-atomic` has two otherwise identical rules,
                  one abrupt with nothing in it and one with a value, and it is that step
                  alone which tells them apart.

                  Deferred rather than used, for the same reason as the entity labels:
                  in `Abrupting` the conclusion `abrupt(V:values) --abrupted(V)-> stuck`
                  is already distinguished by `V:values`, and adding `abrupted.isNotEmpty()`
                  there changes the generated code without changing what it does.
                */
                if (!isPremise) {
                    when (val conclusionExpr = premiseExpr) {
                        is TransitionPremiseWithMutableEntityContext ->
                            stepLabels(conclusionExpr).forEach { (label, _) ->
                                deferredEntityLabels.add(label)
                            }

                        is TransitionPremiseWithControlEntityContext ->
                            getControlEntityLabels(conclusionExpr).forEach { (label, _) ->
                                deferredEntityLabels.add(label)
                            }

                        is TransitionPremiseWithContextualEntityAndStepsContext ->
                            getControlEntityLabels(conclusionExpr.steps()).forEach { (label, _) ->
                                deferredEntityLabels.add(label)
                            }

                        else -> {}
                    }
                }

                val newRewriteData = labels.flatMap { (label, labelObj) -> rewriteEntity(label, labelObj) }
                rewriteData.addAll(newRewriteData)
            }

            fun isTransitionPremise(premiseExpr: PremiseExprContext): Boolean = when (premiseExpr) {
                is TransitionPremiseContext,
                is TransitionPremiseWithMutableEntityContext,
                is TransitionPremiseWithControlEntityContext,
                is TransitionPremiseWithContextualEntityContext,
                is TransitionPremiseWithContextualEntityAndStepsContext,
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

        // Two rules that would match exactly the same values are ambiguous: which one runs
        // would be arbitrary. CBS accepts that, so it is reported rather than guessed at.
        // The step variable is part of the key, because a rule that reduces a subterm
        // gets a `when` case of its own and so cannot clash with one that does not.
        fun clash(ruleObjs: List<Rule>): List<Pair<List<String>, List<String>>> {
            val byKey = LinkedHashMap<Pair<Pair<String, String>?, Set<Condition>>, MutableList<String>>()
            ruleObjs.forEachIndexed { index, ruleObj ->
                byKey.getOrPut(ruleObj.stepVariable to ruleObj.conditions) { mutableListOf() }
                    .add(rules[index].text)
            }
            return byKey.filterValues { clashing -> clashing.size > 1 }
                .map { (key, text) -> key.second.map { it.expr } to text }
        }

        /*
          If the rules cannot be told apart, make the entity labels that could tell them
          apart part of the conditions. This is only reached for a funcon whose rules
          genuinely overlap, so it cannot change the output of one whose rules do not:
          every stable funcon's rules are already distinct, and the baseline depends on
          that. Which rules clashed is reported with them, since the conditions on their
          own are usually one `X.isEmpty()` against another `X.isNotEmpty()`, which
          says nothing about which two rules of which funcon they came from.
        */
        var clashing = clash(ruleObjs)
        if (clashing.isNotEmpty()) {
            ruleObjs.forEach { it.addEntityConditions() }
            clashing = clash(ruleObjs)
        }
        clashing.forEach { (conditions, rules) ->
            throw DetailedException(
                "rules for $name are not distinct: ${rules.size} rules have the same " +
                    "conditions $conditions:\n" + rules.joinToString("\n") { "  $it" }
            )
        }

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

        /*
          One declaration per entity, whatever the number of rules: several of
          `thread-step`'s rules read the same entities, and declaring each of them
          again is a redeclaration. Keyed by variable name and in first-seen order,
          so a funcon with one mutable entity per rule emits what it always did.
        */
        val entityDeclarations = LinkedHashMap<String, String>()
        ruleObjs.forEach { ruleObj ->
            ruleObj.extraMutableReads.forEach { (name, declaration) ->
                entityDeclarations.putIfAbsent(name, declaration)
            }
        }
        prologue.addAll(entityDeclarations.values)

        /*
          A rewrite premise's getter can name the step variable it follows, as
          `thread-activate`'s `map-unite({TI |-> THR}, TM) ~> TM'` does with the `TI`
          that its first premise binds. That variable is assigned where the step is
          taken, which is inside the `when` case, and a local `fun` cannot name a
          variable declared after it. So it is declared here as a `var` and assigned
          in the case: the getter still sees the step's value, and the step is still
          only taken when its case is entered, which hoisting the assignment itself
          would not be. The initial value is never read, because every use of such a
          getter is inside the case that assigns it.
        */
        hoistedStepVars().forEach { stepVarName ->
            prologue.add("var $stepVarName: TermNode = SequenceNode()")
        }

        variables.variables.forEach { (rewrite, varName) ->
            when (varName[0]) {
                'r' -> prologue.add(makeRewriteGetter(varName, rewrite))
                'm' -> prologue.add(makeMatchedGetter(varName, rewrite))
                't' -> prologue.add(makeSteppingGetter(varName, variables.steppedGetters.getValue(varName)))
                else -> Unit
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

        /*
          With one step variable per funcon, an outer case can abort when its
          inner conditions fail, because no other case could have matched. With
          more than one, it has to fall through instead: `postpone` reduces
          `closure give(V, X)` and `closure no-given X`, both of which are
          reducible at every call, and the choice between them is given by the
          contextual entity, not by the subterm. The first case would otherwise
          be entered, find its condition false, and abort without reaching the
          second.

          So the conditions that only mention the prologue are hoisted into the
          outer guard, which leaves the inner `when` for the conditions that
          have to wait for the step. A control entity is deliberately not hoisted:
          it is read after the step, because the step may be what sets it.
        */
        val hoistable = stepVariableSet.size > 1
        val stepVars = stepVariableSet.toList()

        return stepVars.mapIndexed { index, stepVar ->
            val (rewriteLhs, rewriteRhs) = stepVar
            val rulesForStepVar = ruleObjs
                .sortedBy { rule -> rule.rulePriority }
                .filter { rule -> rule.stepVariable == stepVar }

            val condition = if (hoistable) {
                val hoisted = rulesForStepVar.map { rule ->
                    val safe = rule.conditions.filter { cond ->
                        controlReadVars.none { cond.expr.contains(it) }
                    }
                    when {
                        safe.isEmpty() -> null
                        safe.size == rule.conditions.size -> rule.getSortedConditions()
                        else -> safe.sortedBy { it.priority }.joinToString(" && ") { it.expr }
                    }
                }
                val guard = hoisted.filterNotNull().joinToString(" || ") { it.ifEmpty { "true" } }
                if (guard.isEmpty()) "$rewriteLhs.isReducible()"
                else "$rewriteLhs.isReducible() && ($guard)"
            } else {
                "$rewriteLhs.isReducible()"
            }

            /*
              A hoisted step variable is the `var` that the prologue's getters close
              over, so the case assigns it. Declaring a `val` of the same name here
              would shadow that `var` for the rest of the case, and every getter
              called from the case would keep reading the placeholder
              `SequenceNode()` instead of the step's value: `thread-activate` would
              put `set-unite({()}, ATS)` in `active-thread-set` and drop the thread
              it had just created.
             */
            val step = makeVariable(rewriteRhs, "$rewriteLhs.reduce(frame)", init = stepVar.second !in hoistedStepVars())

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
                rulesForStepVar.firstOrNull()?.contextualWrite ?: "",
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
     * Step variables that some rewrite premise's getter names. `thread-activate`
     * binds a fresh thread-id in its first premise and uses it in
     * `set-unite({TI}, ATS) ~> ATS'`, so its getter has to close over a `var` in
     * the prologue rather than the step variable it would otherwise be declared
     * beside. The step variable is only read from inside the `when` case that
     * assigns it, so hoisting the declaration is safe and hoisting the assignment
     * is not: an assignment in the prologue would step the subterm on every call.
     */
    private fun hoistedStepVars(): Set<String> {
        val getterRewrites = variables.variables.filterValues { it[0] in listOf('r', 'm') }.keys
        return variables.variables.filterValues { it[0] == 's' }
            .filterValues { stepVarName ->
                getterRewrites.any { Regex("\\b$stepVarName\\b").containsMatchIn(it) }
            }
            .values
            .toSet()
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

