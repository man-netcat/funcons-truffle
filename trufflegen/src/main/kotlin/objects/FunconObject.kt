package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Param
import main.dataclasses.Rule
import main.dataclasses.Type
import main.objects.Object

class FunconObject(
    name: String,
    ctx: FunconDefinitionContext,
    params: List<Param>,
    val returns: Type,
    aliases: List<String>,
    val builtin: Boolean,
    metaVariables: Set<Pair<String, String>>,
    private val rules: List<RuleDefinitionContext>? = null,
    private val rewritesTo: ExprContext? = null
) : Object(name, ctx, params, aliases, metaVariables) {
    fun makeContent(): String {
        val content = "return " + if (rules != null) {
            val pairs = rules.map { rule ->
                val premises = rule.premises()?.premise()?.toList() ?: emptyList()
                val conclusion = rule.conclusion

                val ruleObj = Rule(premises, conclusion)
                Pair(ruleObj.completeConditions, ruleObj.completeRewrite)
            }

//        if (pairs.any { it.first.isEmpty() }) throw EmptyConditionException(name)

            makeIfStatement(*pairs.toTypedArray(), elseBranch = "fail()")

        } else if (rewritesTo != null) {
            buildRewrite(ctx, rewritesTo)
        } else {
            // TODO Fix
            "${buildTypeRewrite(returns)}()"
        }

        return makeExecuteFunction(content, returnStr)
    }

    val returnStr = if (!returns.computes) {
        buildTypeRewrite(returns, nullable = true)
    } else FCTNODE

    override fun generateCode(): String {
        val skipCriteria = !listOf(
            builtin,               // Builtins should be implemented manually
            paramsAfterVararg > 0  // This behaviour is not yet implemented
        ).any { it }
        val content = if (skipCriteria) makeContent() else todoExecute(returnStr)

        return makeClass(
            nodeName,
            content = content,
            constructorArgs = valueParams,
            typeParams = metaVariables.toList(),
            superClass = emptySuperClass(COMPUTATION),
            annotations = listOf("Funcon")
        )
    }
}

