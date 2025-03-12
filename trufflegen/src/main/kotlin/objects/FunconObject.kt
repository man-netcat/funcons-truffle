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

    override val contentStr: String
        get() = if (!skipCriteria) {
            val reducePairs = params
                .filter { param -> !param.type.computes }
                .map { param ->
                    val paramStr = "p${param.index}"
                    if (!param.type.isVararg) {
                        val condition = "$paramStr !is ValuesNode"
                        val rewrite = "$paramStr.execute(frame)"
                        val paramArgStrs = params.joinToString { innerParam ->
                            val innerParamStr = "p${innerParam.index}"
                            val argStr = if (param.index == innerParam.index) "r" else innerParamStr
                            if (innerParam.type.isVararg) {
                                "$argStr, *$innerParamStr.sliceFrom(1)"
                            } else {
                                argStr
                            }
                        }
                        val newVar = makeVariable("r", rewrite)
                        val newNode = "$nodeName(${paramArgStrs})"
                        condition to "$newVar\n$newNode"
                    } else {
                        // TODO: figure out method for retaining optimisations such as short-circuiting
                        val condition = "$paramStr.any { it !is ValuesNode }"
                        val getFirst = "$paramStr.withIndex().first { it.value !is ValuesNode }"
                        val newVar1 = makeVariable("p", getFirst)
                        val rewrite = "p.value.execute(frame)"
                        val newVar2 = makeVariable("r", rewrite)
                        val paramArgStrs = params.joinToString { innerParam ->
                            val innerParamStr = "p${innerParam.index}"
                            val argStr = if (param.index == innerParam.index) "r" else innerParamStr
                            if (innerParam.type.isVararg) {
                                "*$innerParamStr.sliceUntil(p.index), $argStr, *$innerParamStr.sliceFrom(p.index + 1)"
                            } else {
                                argStr
                            }
                        }
                        val newNode = "$nodeName(${paramArgStrs})"
                        condition to "$newVar1\n$newVar2\n$newNode"
                    }
                }


            val body = if (rewritesTo != null) {
                // Has single context-insensitive rewrite
                val rewriteStr = rewrite(ctx, rewritesTo)
                val whenStmt = if (reducePairs.isNotEmpty()) {
                    makeWhenStatement(reducePairs, elseBranch = rewriteStr)
                } else rewriteStr
                "val new = $whenStmt\nreturn replace(new)"
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

                val whenStmt = makeWhenStatement(
                    emptyPairs + reducePairs + pairs,
                    elseBranch = "abort(\"$name\")"
                )

                // Concatenate intermediates and whenStmt
                "val new = $whenStmt\nreturn replace(new)"
            } else {
                // TODO: Fix me
//                println("no rules: ${ctx.text}")
                // Has no rules
                "TODO(\"FIXME\")"
            }

            makeExecuteFunction(body, TERMNODE)
        } else todoExecute(name, TERMNODE)


    override val annotations: List<String>
        get() = listOf("CBSFuncon")

    override val keyWords: List<String> = emptyList()
}

