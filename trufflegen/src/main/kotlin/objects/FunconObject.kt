package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Rule
import main.dataclasses.Type
import main.exceptions.EmptyConditionException
import main.objects.Object

class FunconObject(
    ctx: FunconDefinitionContext,
    metaVariables: Set<Pair<String, String>>,
    val returns: Type,
    private val builtin: Boolean,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val rewritesTo: ExprContext? = null
) : Object(ctx, metaVariables) {
    private val skipCriteria: Boolean
        get() = listOf(
            builtin,               // Builtins should be implemented manually
            paramsAfterVararg > 0  // This behaviour is not yet implemented
        ).contains(true)

    private val returnStr: String
        get() = returns.rewrite(inNullableExpr = true, full = false)

    override val contentStr: String
        get() = if (!skipCriteria) {
            val paramStrs = getParamStrs(ctx, isParam = false)
            paramStrs.map { (valueExpr, typeExpr, paramStr) ->
                val type = Type(typeExpr, isParam = false)
                if (!type.computes) {
                    // TODO
//                println("valueExpr: ${valueExpr?.text}, type: $type, paramStr: $paramStr")
                }
            }

            val body = "return " + if (rewritesTo != null) {
                // Has single context-insensitive rewrite
                rewrite(ctx, rewritesTo)
            } else if (rules.isNotEmpty()) {
                // Has one or more rewrite rules
                val ruleObjs = rules.map { rule ->
                    val premises = rule.premises()?.premiseExpr()?.toList() ?: emptyList()
                    val conclusion = rule.conclusion

                    Rule(premises, conclusion, returns)
                }

                if (ruleObjs.isEmpty() || ruleObjs.any { ruleObj ->
                        ruleObj.conditionStr.isBlank()
                    }) throw EmptyConditionException(name)

                val pairs = ruleObjs.map { rule ->
                    rule.conditionStr to rule.bodyStr
                }
                makeWhenStatement(pairs, elseBranch = "fail()")

            } else {
                // Has no rules
                // TODO This is definitely wrong, fix
                "TODO(\"FIXME\")"
            }

            makeExecuteFunction(body, returnStr)
        } else todoExecute(returnStr)

    override val annotations: List<String>
        get() = listOf("CBSFuncon")

    override val keyWords: List<String> = emptyList()
}

