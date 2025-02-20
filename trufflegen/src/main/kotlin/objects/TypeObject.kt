package main.objects

import cbs.CBSParser.FunconExpressionContext
import cbs.CBSParser.TypeDefinitionContext
import main.*
import main.exceptions.DetailedException

class TypeObject(
    ctx: TypeDefinitionContext,
    metaVariables: Set<Pair<String, String>>,
) : Object(ctx, metaVariables) {
    override val keyWords: List<String> = listOf("abstract")
    override val annotations: List<String> = listOf("CBSType")
    private val definitions = if (ctx.definition != null) extractAndOrExprs(ctx.definition) else emptyList()
    val operator = ctx.op?.text
    val builtin = ctx.modifier != null
    override val superClassStr: String
        get() = when (definitions.size) {
            0 -> {
                val superClassName = if (name != "value-types") {
                    toClassName("value-types")
                } else FCTNODE
                emptySuperClass(superClassName)
            }

            1 -> {
                val definition = definitions[0]
                if (definition is FunconExpressionContext) {
                    val defType = getObject(definition)
                    when (defType) {
                        is TypeObject -> println("TypeObject: ${defType.name}")
                        is AlgebraicDatatypeObject -> println("AlgebraicDatatypeObject: ${defType.name}")
                        is SupertypeDatatypeObject -> println("SupertypeDatatypeObject: ${defType.name}")
                    }
                    makeFunCall(defType.nodeName, args = listOf(), typeParams = setOf())
                } else throw DetailedException("Unexpected definition ${definition.text}")
            }

            2 -> "" // TODO: Fix this edge case
            else -> throw DetailedException("Unexpected amount of definitions, ${definitions.joinToString()} has ${definitions.size} items")
        }
}