package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Param
import main.dataclasses.Rule
import main.dataclasses.Type
import main.exceptions.EmptyConditionException
import main.objects.Object

class FunconObject(
    name: String,
    ctx: FunconDefinitionContext,
    params: List<Param>,
    val returns: Type,
    aliases: List<String>,
    val builtin: Boolean,
    metaVariables: Set<Pair<String, String>>,
    private val rules: List<RuleDefinitionContext> = emptyList(),
    private val rewritesTo: ExprContext? = null
) : Object(name, ctx, params, aliases, metaVariables) {
    fun makeContent(): String {
        val content = "return " + if (rewritesTo != null) {
            // Has single context-insensitive rewrite
            rewrite(ctx, rewritesTo)
        } else if (rules.isNotEmpty()) {
            // Has one or more rewrite rules
            val pairs = rules.map { rule ->
                val premises = rule.premises()?.premiseExpr()?.toList() ?: emptyList()
                val conclusion = rule.conclusion

                val ruleObj = Rule(premises, conclusion)
                Pair(ruleObj.conditions, ruleObj.rewrite)
            }

            if (pairs.isEmpty() || pairs.any { it.first.isEmpty() }) throw EmptyConditionException(name)

            makeIfStatement(*pairs.toTypedArray(), elseBranch = "fail()")

        } else {
            // Has no rules
            // TODO This is definitely wrong, fix
            "${rewriteType(returns)}()"
        }

        return makeExecuteFunction(content, returnStr)
    }

    val returnStr = rewriteType(returns, nullable = true)

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

