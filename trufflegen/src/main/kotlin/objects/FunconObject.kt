package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Rule
import main.dataclasses.Type
import main.objects.Object

class FunconObject(
    ctx: FunconDefinitionContext,
    metaVariables: Set<Pair<String, String>>,
    val returns: Type,
    private val builtin: Boolean,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val rewritesTo: ExprContext? = null,
) : Object(ctx, metaVariables) {
    private val skipCriteria: Boolean
        get() = listOf(
            builtin,               // Builtins should be implemented manually
            paramsAfterVararg > 0  // This behaviour is not yet implemented
        ).contains(true)

    private val returnStr: String
        //        get() = returns.rewrite(inNullableExpr = true, full = false)
        get() {
            val inner = FCTNODE + if (returns.isNullable) "?" else ""
            return if (returns.isArray) "Sequence<${inner}>" else inner
        }

    override val contentStr: String
        get() = if (!skipCriteria) {
            val body = if (rewritesTo != null) {
                // Has single context-insensitive rewrite
                "return ${rewrite(ctx, rewritesTo)}"
            } else if (rules.isNotEmpty()) {
                // Has one or more rewrite rules
                val ruleObjs = rules.map { rule ->
                    val premises = rule.premises()?.premiseExpr()?.toList() ?: emptyList()
                    val conclusion = rule.conclusion

                    Rule(premises, conclusion, returns)
                }

                val (ruleWithEmpty, rule) = ruleObjs.partition { it.emptyConditions.isNotEmpty() }

                val emptyPairs = ruleWithEmpty.map { rule ->
                    rule.emptyConditions.joinToString(" && ") to rule.bodyStr
                }
                val pairs = rule.map { rule ->
                    rule.conditions.joinToString(" && ") to rule.bodyStr
                }

                val reducePairs = params
                    .filter { param -> !param.type.computes }
                    .map { param ->
                        val paramStr = "p${param.index}"
                        val reducedStr = "r${param.index}"
                        val varargIndexStr = if (param.type.isVararg) "[0]" else ""
                        val condition = "$paramStr$varargIndexStr !is ValuesNode"
                        val rewrite = "$paramStr${varargIndexStr}.execute(frame) as $FCTNODE"
                        val newVar = makeVariable(reducedStr, rewrite)
                        val paramArgStrs = params.joinToString { innerParam ->
                            val innerParamStr = "p${innerParam.index}"
                            val argStr = if (param.index == innerParam.index) reducedStr else innerParamStr
                            if (innerParam.type.isVararg) {
                                "$argStr, *$innerParamStr.sliceFrom(1)"
                            } else {
                                argStr
                            }
                        }
                        val metavarStr = if (metaVariables.isNotEmpty()) {
                            "<${metaVariables.joinToString { it.first }}>"
                        } else ""
                        val newNode = "$nodeName$metavarStr(${paramArgStrs})"
                        condition to "$newVar\n$newNode"
                    }

                val whenStmt = makeWhenStatement(emptyPairs + reducePairs + pairs, elseBranch = "abort()")

                // Concatenate intermediates and whenStmt
                "val new = $whenStmt\nreturn replace(new).execute(frame)"
            } else {
                println("no rules: ${ctx.text}")
                // Has no rules
                "TODO(\"FIXME\")"
            }

            makeExecuteFunction(body, returnStr)
        } else todoExecute(name, "Any")


    override val annotations: List<String>
        get() = listOf("CBSFuncon")

    override val keyWords: List<String> = emptyList()
}

