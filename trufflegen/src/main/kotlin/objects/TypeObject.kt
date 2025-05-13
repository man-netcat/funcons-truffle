package main.objects

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException

class TypeObject(
    ctx: TypeDefinitionContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
) : Object(ctx, metaVariables) {
    val operator = ctx.op?.text
    override val keyWords: List<String> = listOf("open")
    private val definitionExpr = ctx.definition
    override val superClassStr: String
        get() {
            fun makeTypeDef(definition: ExprContext): String {
                return if (definition is FunconExpressionContext) {
                    val defType = getObject(definition)
                    val args = extractArgs(definition)
                    fun rewriteArg(arg: ExprContext): String {
                        return when (arg) {
                            is NumberContext -> "IntegerNode(${arg.text})"
                            is FunconExpressionContext -> {
                                val args = extractArgs(arg)
                                val argStr = args.joinToString { arg -> rewriteArg(arg) }
                                val funcon = toNodeName(arg.name.text)
                                "${funcon}($argStr)"
                            }

                            is SuffixExpressionContext -> rewriteArg(arg.operand)
                            is VariableContext -> {
                                if (params.any { param -> param.value == arg.text }) {
                                    val tpIndex = params.indexOfFirst { param -> param.value == arg.text }
                                    "${asVarName}Tp${tpIndex}"
                                } else if (arg.text in metavariableMap.keys) {
                                    rewrite(definition, metavariableMap[arg.text]!!)
                                } else throw DetailedException("Unexpected arg with type ${arg::class.simpleName} ${arg.text}")
                            }

                            else -> throw DetailedException("Unexpected arg with type ${arg::class.simpleName} ${arg.text}")
                        }
                    }

                    val valueArgStrs = args.map { arg -> rewriteArg(arg) }
                    makeFunCall(defType.nodeName, args = valueArgStrs)
                } else throw DetailedException("Unexpected definition ${definition.text}")
            }

            val definitions = if (definitionExpr != null) extractAndOrExprs(definitionExpr) else emptyList()
            return when (definitions.size) {
                0 -> {
                    val superClassName = when (name) {
                        "abstractions" -> toNodeName("values")
                        else -> toNodeName("ground-values")
                    }
                    emptySuperClass(superClassName)
                }

                1 -> makeTypeDef(definitions[0])
                else -> {
                    val nodeType = when (definitionExpr) {
                        is AndExpressionContext -> "IntersectionTypeNode"
                        is OrExpressionContext -> "UnionTypeNode"
                        else -> throw DetailedException("Unexpected expression type: ${definitionExpr::class.simpleName}, ${definitionExpr.text}")
                    }
                    "$nodeType(${definitions.joinToString { definition -> makeTypeDef(definition) }})"
                }
            }
        }

    val elementInBody: String
        get() = if (definitionExpr != null && name !in builtinOverride) {
            val conditionStr = makeTypeCondition("this", definitionExpr)
            makeIsInTypeFunction(camelCaseName, "return $conditionStr")
        } else ""
}