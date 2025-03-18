package objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Rule
import main.dataclasses.Type
import main.exceptions.DetailedException
import main.objects.Object

class FunconObject(
    ctx: FunconDefinitionContext,
    metaVariables: Set<Pair<String, String>>,
    val returns: Type,
    val rules: List<RuleDefinitionContext> = emptyList(),
    val rewritesTo: ExprContext? = null,
) : Object(ctx, metaVariables) {
    fun makeReducePairs(): List<Pair<String, String>> {
        return params
            .filter { param -> !param.type.computes }
            .map { param ->
                val paramStr = "p${param.index}"
                if (!param.type.isSequence) {
                    val condition = "$paramStr !is ValuesNode"
                    val reduced = "$paramStr.reduce(frame)"
                    val paramArgStrs = params.joinToString { innerParam ->
                        val innerParamStr = "p${innerParam.index}"
                        val argStr = if (param.index == innerParam.index) "r" else innerParamStr
                        if (innerParam.type.isSequence) {
                            "$argStr, $innerParamStr.tail"
                        } else argStr
                    }
                    val newVar = makeVariable("r", value = reduced)
                    val newNode = "$nodeName(${paramArgStrs})"
                    condition to "$newVar\n$newNode"
                } else {
                    val condition = "$paramStr.isReducible()"
                    val reduced = "$paramStr.reduce(frame)"
                    val newVar = makeVariable("r", value = reduced)
                    val paramArgStrs = params.joinToString { innerParam ->
                        val innerParamStr = "p${innerParam.index}"
                        val argStr = if (param.index == innerParam.index) "r" else innerParamStr
                        if (innerParam.type.isSequence) "r" else argStr
                    }
                    val newNode = "$nodeName(${paramArgStrs})"
                    condition to "$newVar\n$newNode"
                }
            }
    }

    override val contentStr: String
        get() {
//            val reducePairs = makeReducePairs()
            val reducibles = params.mapIndexedNotNull { index, param -> if (!param.type.computes) index else null }
            val reduceComputations = if (reducibles.isNotEmpty()) {
                "reduceComputations(frame, listOf(${reducibles.joinToString()}))?.let { reduced -> return replace(reduced) }\n\n"
            } else ""

            val body = if (rewritesTo != null) {
                // Has single context-insensitive rewrite
                val rewriteStr = rewrite(ctx, rewritesTo)
//                val whenStmt = if (reducePairs.isNotEmpty()) {
//                    makeWhenStatement(reducePairs, elseBranch = rewriteStr)
//                } else rewriteStr
                "${reduceComputations}val new = $rewriteStr\nreturn replace(new)"
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
                    emptyPairs + pairs,
                    elseBranch = "abort(\"$name\")"
                )

                // Concatenate intermediates and whenStmt
                "${reduceComputations}val new = $whenStmt\nreturn replace(new)"
            } else throw DetailedException("Funcon $name does not have any associated rules.")

            return makeReduceFunction(body, TERMNODE)
        }


    override val annotations: List<String>
        get() = listOf("CBSFuncon")

    override val keyWords: List<String> = emptyList()
}

