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
    fun getRuleObj(rule: RuleDefinitionContext): Rule {
        val premises = rule.premises()?.premiseExpr()?.toList() ?: emptyList()
        val conclusion = rule.conclusion

        return Rule(premises, conclusion, returns)
    }

    fun makeContent(): String {
        val paramStrs = getParamStrs(ctx, isParam = false)
        paramStrs.map { (valueExpr, typeExpr, paramStr) ->
            val type = Type(typeExpr, isParam = false)
            if (!type.computes) {
                // TODO
//                println("valueExpr: ${valueExpr?.text}, type: $type, paramStr: $paramStr")
            }
        }

        val content = "return " + if (rewritesTo != null) {
            // Has single context-insensitive rewrite
            rewrite(ctx, rewritesTo)
        } else if (rules.isNotEmpty()) {
            // Has one or more rewrite rules
            val ruleObjs = rules.map { rule -> getRuleObj(rule) }

            if (ruleObjs.isEmpty() || ruleObjs.any { ruleObj ->
                    ruleObj.conditions.isEmpty()
                }) throw EmptyConditionException(name)

            val pairs = ruleObjs.map { rule ->
                rule.conditionStr to rule.bodyStr
            }
            makeIfStatement(pairs, elseBranch = "fail()")

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
            superClass = emptySuperClass(COMPUTATION),
            annotations = listOf("Funcon"),
            typeParams = emptySet()
        )
    }
}

