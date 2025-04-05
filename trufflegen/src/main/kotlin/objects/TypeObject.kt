package main.objects

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException

class TypeObject(
    ctx: TypeDefinitionContext,
) : Object(ctx) {
    val operator = ctx.op?.text
    override val keyWords: List<String> = listOf("open")
    private val definitionExpr = ctx.definition
    override val superClassStr: String
        get() {
            fun makeTypeDef(definition: ExprContext): String {
                return if (definition is FunconExpressionContext) {
                    val defType = getObject(definition)
                    val args = extractArgs(definition)
                    val valueArgStrs = args.map { arg -> rewrite(ctx, arg) }
                    makeFunCall(defType.nodeName, args = valueArgStrs)
                } else throw DetailedException("Unexpected definition ${definition.text}")
            }

            val definitions = if (definitionExpr != null) extractAndOrExprs(definitionExpr) else emptyList()
            return when (definitions.size) {
                0 -> {
                    val superClassName = when (name) {
                        "abstractions" -> toClassName("values")
                        else -> toClassName("ground-values")
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
    override val contentStr: String
        get() = when (operator) {
            null -> ""
            "<:" -> makeValueTypesCompanionObject("TODO(\"Implement me!\")")
            "~>" -> ""
            else -> throw DetailedException("Unexpected operator: $operator")
        }
}