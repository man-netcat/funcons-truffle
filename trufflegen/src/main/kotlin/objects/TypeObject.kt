package main.objects

import cbs.CBSParser.*
import main.*
import main.exceptions.DetailedException

class TypeObject(
    ctx: TypeDefinitionContext,
    metaVariables: Set<Pair<String, String>>,
) : Object(ctx, metaVariables) {
    override val keyWords: List<String> = listOf("open")
    override val annotations: List<String> = listOf("CBSType")
    private val definitions = if (ctx.definition != null) extractAndOrExprs(ctx.definition) else emptyList()
    val operator = ctx.op?.text
    override val superClassStr: String
        get() {
            return when (definitions.size) {
                0 -> {
                    val superClassName = toClassName("ground-values")
                    emptySuperClass(superClassName)
                }

                1 -> {
                    val definition = definitions[0]
                    if (definition is FunconExpressionContext) {
                        val defType = getObject(definition)
                        val args = extractArgs(definition)
                        val (valueArgs, typeArgs) = args.partition { arg -> arg is TypeExpressionContext || arg is NumberContext }
                        val valueArgStrs = valueArgs.map { arg -> rewrite(ctx, arg) }
                        //                    val typeArgStrs = typeArgs.map { arg -> Type(arg).rewrite(inNullableExpr = true) }
                        makeFunCall(defType.nodeName, args = valueArgStrs)
                    } else throw DetailedException("Unexpected definition ${definition.text}")
                }

                2 -> "" // TODO: Fix this edge case
                else -> throw DetailedException("Unexpected amount of definitions, ${definitions.joinToString()} has ${definitions.size} items")
            }
        }
}