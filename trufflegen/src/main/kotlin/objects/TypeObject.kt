package main.objects

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException

class TypeObject(
    ctx: TypeDefinitionContext,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
    registry: Registry,
) : Object(ctx, metaVariables, registry) {
    val operator = ctx.op?.text
    /**
     * The type this one is declared a subtype of, for `Type N ~> T`. CBS treats
     * that as "N is a T", so `isIn<T>()` has to accept N's values too.
     *
     * The grammar also allows `<:` as a type's `op`, and that spelling is not
     * read here: every type in the corpus declares membership with `~>`, as
     * Strings.cbs does with `strings ~> lists(characters)`.
     */
    val supertypeName: String? =
        if (operator == "~>") (ctx.definition as? FunconExpressionContext)?.name?.text else null

    override val keyWords: List<String> = listOf("open")
    private val definitionExpr = ctx.definition
    override val superClassStr: String
        get() {
            fun makeTypeDef(definition: ExprContext): String {
                return if (definition is FunconExpressionContext) {
                    val defType = getObject(registry, definition)
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
                                    rewrite(registry, definition, metavariableMap[arg.text]!!)
                                } else throw DetailedException("Unexpected arg with type ${arg::class.simpleName} ${arg.text}")
                            }

                            else -> throw DetailedException("Unexpected arg with type ${arg::class.simpleName} ${arg.text}")
                        }
                    }

                    val valueArgStrs = args.map { arg -> rewriteArg(arg) }
                    /*
                      A variadic type's parameters are a single sequence. `tuples(T*)` is
                      declared with one `tuplesTp0: SequenceNode` rather than one parameter
                      per argument, so `tuples(ground-values, thread-ids)` has to hand it
                      one sequence holding both. Passing them as two arguments gave
                      `TuplesNode(GroundValuesNode(), ThreadIdsNode())`, which is neither
                      arity nor type right.
                     */
                    if (defType.params.size == 1 && defType.params[0].type.isSequence) {
                        makeFunCall(defType.nodeName, args = listOf("SequenceNode(${valueArgStrs.joinToString(", ")})"))
                    } else {
                        makeFunCall(defType.nodeName, args = valueArgStrs)
                    }
                } else throw DetailedException("Unexpected definition ${definition.text}")
            }

            val definitions = if (definitionExpr != null) extractAndOrExprs(definitionExpr) else emptyList()
            return when (definitions.size) {
                0 -> {
                    val superClassName = toNodeName("value-types")
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

    override val elementInBody: String
        get() = if (definitionExpr != null && !registry.isBuiltin(name)) {
            val conditionStr = makeTypeCondition(registry, "this", definitionExpr)
            makeIsInTypeFunction(camelCaseName, "return ${withSubtypesOf(registry, name, conditionStr)}")
        } else ""
}