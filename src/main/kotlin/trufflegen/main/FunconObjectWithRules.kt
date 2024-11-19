package trufflegen.main

import trufflegen.antlr.CBSParser.*
import kotlin.collections.mapNotNull
import kotlin.collections.orEmpty

class FunconObjectWithRules(
    name: String,
    def: FunconDefinitionContext,
    params: List<Param>,
    private val rules: List<RuleDefinitionContext>,
    returns: ReturnType,
    aliases: List<AliasDefinitionContext>,
    builtin: Boolean,
    metavariables: Map<String, String>
) : FunconObject(name, def, params, returns, aliases, builtin, metavariables) {
    private fun complementStr(bool: Boolean): String = if (bool) "!" else ""
    private fun isInputOutputEntity(stepExpr: StepExprContext): Boolean {
        val steps = stepExpr.steps().step()
        val labels = steps.firstOrNull()?.labels()?.label()
        return steps.size == 1 && labels?.size == 1 && labels.firstOrNull()?.polarity != null
    }

    private fun gatherLabels(stepExpr: StepExprContext): List<LabelContext> {
        return if (stepExpr.context_?.name != null) {
            listOf(stepExpr.context_)
        } else {
            stepExpr.steps()?.step()?.sortedBy { step -> step.sequenceNumber?.text?.toInt() }?.flatMap { step ->
                step.labels()?.label()?.filter { label -> label.value != null }.orEmpty()
            }.orEmpty()
        }
    }

    private fun processConclusion(
        conclusion: PremiseContext, entityMap: Map<String, String>
    ): Triple<ExprContext, String, String> {
        fun argsConditions(def: FunconExpressionContext): String {
            val args = when (val argContext = def.args()) {
                is MultipleArgsContext -> argContext.exprs().expr()
                is ListIndexExpressionContext -> argContext.indices.expr()
                is SingleArgsContext -> listOf(argContext.expr())
                else -> emptyList()
            }

            return args.mapIndexedNotNull { argIndex, arg ->
                fun processListTuple(paramStr: String, elementContainer: ExprsContext?): String {
                    return if (elementContainer == null) {
                        "${paramStr}.isEmpty()"
                    } else {
                        val elements = elementContainer.expr()
                        if (elements.size != 1) {
                            throw DetailedException("Unexpected amount of list/tuple values: ${elements.size}")
                        } else {
                            val element = elements[0]
                            if (element is TypeExpressionContext) {
                                val typeStr = buildTypeRewrite(ReturnType(element.type))
                                "$paramStr is $typeStr"
                            } else throw DetailedException("Unexpected element type: ${element::class.simpleName}, ${element.text}")
                        }
                    }
                }

                val (argValue, argTypeExpr) = if (arg is TypeExpressionContext) arg.value to arg.type else arg to null

                val paramStr = buildRewrite(def, argValue, makeParamStr = true, forcedArgIndex = argIndex)
                val valueCondition = when (argValue) {
                    is FunconExpressionContext -> "$paramStr is ${buildTypeRewrite(ReturnType(argValue))}"
                    is NumberContext -> "$paramStr == ${argValue.text}"
                    is ListExpressionContext -> processListTuple(paramStr, argValue.elements)
                    is TupleExpressionContext -> processListTuple(paramStr, argValue.elements)

                    is VariableContext, is VariableStepContext, is SuffixExpressionContext -> null
                    else -> throw IllegalArgumentException("Unexpected arg type: ${argValue::class.simpleName}")
                }

                val typeCondition = if (argTypeExpr != null) {
                    val argType = ReturnType(argTypeExpr)
                    val rewritten = buildTypeRewrite(argType)
                    "$paramStr ${complementStr(argType.complement)}is $rewritten"
                } else null

                if (typeCondition != null && valueCondition != null) {
                    "$valueCondition && $typeCondition"
                } else if (typeCondition != null) typeCondition else valueCondition
            }.joinToString(" && ")
        }


        return when (conclusion) {
            is RewritePremiseContext -> {
                val rewrite = buildRewrite(conclusion.lhs, conclusion.rhs)
                val conditions = if (conclusion.lhs is FunconExpressionContext) {
                    argsConditions(conclusion.lhs as FunconExpressionContext)
                } else ""
                Triple(conclusion.lhs, conditions, rewrite)
            }

            is StepPremiseContext -> {
                val stepExpr = conclusion.stepExpr()
                if (isInputOutputEntity(stepExpr)) return Triple(
                    stepExpr.lhs, "TODO(\"Condition\")", "TODO(\"Content\")"
                )
                val entityStr = gatherLabels(stepExpr).joinToString("\n") { label ->
                    entityMap(label.name.text) + " = " + if (label.value != null) {
                        buildRewrite(stepExpr.lhs, label.value, entityMap)
                    } else "null"
                }
                var rewriteStr = buildRewrite(stepExpr.lhs, stepExpr.rhs, entityMap)
                rewriteStr = if (entityStr.isNotEmpty()) "$entityStr\n$rewriteStr" else rewriteStr
                val conditions = if (stepExpr.lhs is FunconExpressionContext) {
                    argsConditions(stepExpr.lhs as FunconExpressionContext)
                } else ""
                Triple(stepExpr.lhs, conditions, rewriteStr)
            }

            is MutableEntityPremiseContext -> {
                val mutableExpr = conclusion.mutableExpr()
                val rewrite = buildRewrite(mutableExpr.lhs, mutableExpr.rhs, entityMap)
                val conditions = if (mutableExpr.lhs is FunconExpressionContext) {
                    argsConditions(mutableExpr.lhs as FunconExpressionContext)
                } else ""
                Triple(mutableExpr.lhs, conditions, rewrite)
                // TODO: also modify the entity
            }

            else -> throw DetailedException("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }
    }

    private fun processPremises(
        premises: List<PremiseContext>, ruleDef: ExprContext, entityMap: Map<String, String>
    ): Pair<String, String> {
        val result = premises.map { premise ->
            when (premise) {
                is StepPremiseContext -> {
                    val stepExpr = premise.stepExpr()
                    val labelConditions = gatherLabels(stepExpr).joinToString(" && ") { label ->
                        entityMap(label.name.text) + " == " + (if (label.value != null) {
                            buildRewrite(ruleDef, label.value)
                        } else "null")
                    }
                    val rewriteLhs = buildRewrite(ruleDef, stepExpr.lhs, entityMap)
                    val rewriteRhs = buildRewrite(ruleDef, stepExpr.rhs, entityMap)
                    val condition = "$rewriteLhs is $COMPUTATION"
                    val rewrite = "val $rewriteRhs = $rewriteLhs.execute(frame)"

                    if (labelConditions.isNotBlank()) Pair("$condition && $labelConditions", rewrite)
                    else Pair(condition, rewrite)
                }

                is RewritePremiseContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val rewriteRhs = buildRewrite(ruleDef, premise.rhs)
                    val rewrite = "val $rewriteRhs = $rewriteLhs"
                    Pair(null, rewrite)
                }

                is BooleanPremiseContext -> {
                    val rewriteLhs = buildRewrite(ruleDef, premise.lhs)
                    val (op, rewriteRhs) = when (premise.rhs) {
                        is FunconExpressionContext -> {
                            val type = ReturnType(premise.rhs)
                            val rewriteRhs = buildTypeRewrite(type)
                            "${complementStr(type.complement)}is" to rewriteRhs
                        }

                        else -> {
                            when (premise.op.text) {
                                "==" -> "=="
                                "=/=" -> "!="
                                else -> throw DetailedException("Unexpected operator type: ${premise.op.text}")
                            } to buildRewrite(ruleDef, premise.rhs)
                        }
                    }
                    val condition = "$rewriteLhs $op $rewriteRhs"
                    Pair(condition, null)
                }

                is TypePremiseContext -> {
                    val value = buildRewrite(ruleDef, premise.value)
                    val type = ReturnType(premise.type)
                    val typeStr = buildTypeRewrite(type)
                    val condition = "$value ${complementStr(type.complement)}is $typeStr"
                    Pair(condition, null)
                }

                else -> throw DetailedException("Unexpected premise type: ${premise::class.simpleName}")
            }
        }

        val conditions = result.mapNotNull { it.first }.joinToString(" && ")
        val rewrites = result.mapNotNull { it.second }.joinToString("\n")

        return Pair(conditions, rewrites)
    }

    private fun buildEntityMap(premises: List<PremiseContext>, conclusion: PremiseContext): Map<String, String> {
        fun extractValue(labelValue: ExprContext): String {
            val rewritten = when (labelValue) {
                is TypeExpressionContext -> labelValue.value.text
                is VariableContext -> labelValue.text
                is VariableStepContext -> makeVariableStepName(labelValue)
                is FunconExpressionContext -> buildRewrite(labelValue, labelValue)
                is SuffixExpressionContext -> "TODO"
                else -> throw DetailedException("Unexpected labelValue type: ${labelValue::class.simpleName}, ${labelValue.text}")
            }
            return rewritten
        }

        return (premises + conclusion).flatMap { premise ->
            fun labelData(label: LabelContext) = extractValue(label.value) to label.name.text
            if (premise is StepPremiseContext) {
                val stepExpr = premise.stepExpr()
                if (stepExpr.context_?.name != null) {
                    if (stepExpr.context_.value != null) {
                        listOf(labelData(stepExpr.context_))
                    } else emptyList()
                } else {
                    stepExpr.steps()?.step()?.sortedBy { step -> step.sequenceNumber?.text?.toInt() }?.flatMap { step ->
                        step.labels()?.label()?.filter { label -> label.value != null }?.map { label ->
                            labelData(label)
                        }.orEmpty()
                    }.orEmpty()
                }
            } else if (premise is MutableEntityPremiseContext) {
                val mutableExpr = premise.mutableExpr()
                val lhsLabel = mutableExpr.entityLhs
                val rhsLabel = mutableExpr.entityRhs
                listOf(labelData(lhsLabel), labelData(rhsLabel))
            } else emptyList()
        }.toMap()
    }

    override fun makeContent(): String {
        val pairs = rules.map { rule ->
            val premises = rule.premises()?.premise()?.toList() ?: emptyList()
            val conclusion = rule.conclusion

            val entityMap = buildEntityMap(premises, conclusion)

            val (ruleDef, conclusionConditions, conclusionRewrite) = processConclusion(conclusion, entityMap)

            val (premiseConditions, premiseRewrite) = processPremises(premises, ruleDef, entityMap)

            val finalConditions =
                listOf(premiseConditions, conclusionConditions).filter { it.isNotEmpty() }.joinToString(" && ")

            val finalRewrite = listOf(premiseRewrite, conclusionRewrite).filter { it.isNotEmpty() }.joinToString("\n")

            Pair(finalConditions, finalRewrite)
        }

        if (pairs.any { it.first.isEmpty() }) throw EmptyConditionException(name)

        val content = "return " + makeIfStatement(*pairs.toTypedArray(), elseBranch = "fail()")

        return makeExecuteFunction(content, returnStr)
    }

}