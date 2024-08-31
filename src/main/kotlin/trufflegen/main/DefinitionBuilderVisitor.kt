package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class DefinitionBuilderVisitor : CBSBaseVisitor<Unit>() {
    private val objects = mutableListOf<ObjectDefinition>()

    private val primitives = mutableListOf<String>()

    fun getObjects(): List<ObjectDefinition> = objects

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        val name = funcon.name.text

        val modifierText = funcon.modifier?.text.orEmpty()
        println("${modifierText}Funcon: $name")

        val params = funcon.params()?.param()?.mapIndexed { index, param ->
            Param(index, param.value, param.type)
        } ?: emptyList()

        val returns = ReturnType(funcon.returnType)
        val aliases = funcon.aliasDefinition()

        val dataContainer = if (funcon.rewritesTo != null) {
            val rewritesTo = funcon.rewritesTo
            FunconWithRewrite(funcon, name, params, rewritesTo, returns, aliases)
        } else {
            val rules = funcon.ruleDefinition()
            FunconWithRules(funcon, name, params, rules, returns, aliases)
        }

        objects.add(dataContainer)
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext) {
        val name = datatype.name.text

        val modifierText = datatype.modifier?.text.orEmpty()
        println("${modifierText}Datatype: $name")

        val datatypePrimitives = mutableListOf<String>()
        val datatypeComposites = mutableListOf<ExprContext>()

        // Uncomment and implement processing if needed
        // datatype.datatypeDefs().expr().map { def ->
        //     println(def::class)
        // }

        // val dataContainer = DatatypeDefinitionData(name, datatypePrimitives, datatypeComposites)

        // objects.add(dataContainer)
    }
}
