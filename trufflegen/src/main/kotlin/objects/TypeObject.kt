package main.objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Param
import main.dataclasses.Type
import main.exceptions.DetailedException

class TypeObject(
    name: String,
    ctx: TypeDefinitionContext,
    params: List<Param>,
    metaVariables: Set<Pair<String, String>>,
    aliases: List<String>,
    private val definitions: List<ExprContext>
) : Object(name, ctx, params, aliases, metaVariables) {
    override val keyWords: List<String> = listOf("abstract")
    override val annotations: List<String> = listOf("Type")
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
                    val funconObj = globalObjects[definition.name.text]!!
                    val args = extractArgs(definition)
                    val typeParams = args.map { arg -> Type(arg).rewrite() }.toSet()
                    makeFunCall(funconObj.nodeName, typeParams = typeParams)
                } else throw DetailedException("Unexpected definition ${definition.text}")
            }

            2 -> "" // TODO: Fix this edge case
            else -> throw DetailedException("Unexpected amount of definitions, ${definitions.joinToString()} has ${definitions.size} items")
        }
}