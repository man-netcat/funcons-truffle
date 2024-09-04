package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class ObjectBuilderVisitor : CBSBaseVisitor<Unit>() {
    private val objects = mutableListOf<Object>()

    private val metavariables = mutableMapOf<ExprContext, ExprContext>()

    fun getObjects(): List<Object> = objects

    override fun visitMetavariablesDefinition(metavars: MetavariablesDefinitionContext) {
        metavariables.putAll(metavars.variables.expr().mapNotNull { variable ->
            variable to metavars.definition
        }.toMap())
    }

    private fun extractParams(obj: ParseTree): List<Param> {
        val params = when (obj) {
            is FunconDefinitionContext -> obj.params()
            is DatatypeDefinitionContext -> obj.params()
            is TypeDefinitionContext -> obj.params()
            else -> throw DetailedException("Unexpected object: ${obj::class.simpleName}")
        }

        return params?.param()?.mapIndexed { index, param ->
            Param(index, param.value, param.type)
        } ?: emptyList()
    }

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        val name = funcon.name.text

        val modifierText = funcon.modifier?.text.orEmpty()
        println("${modifierText}Funcon: $name")

        val params = extractParams(funcon)

        val returns = ReturnType(funcon.returnType)
        val aliases = funcon.aliasDefinition()

        val dataContainer = if (funcon.rewritesTo != null) {
            val rewritesTo = funcon.rewritesTo
            FunconObjectWithRewrite(funcon, name, params, rewritesTo, returns, aliases)
        } else {
            val rules: List<RuleDefinitionContext> = funcon.ruleDefinition()
            FunconObjectWithRules(funcon, name, params, rules, returns, aliases)
        }

        objects.add(dataContainer)
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext) {
        val name = datatype.name.text

        val modifierText = datatype.modifier?.text.orEmpty()
        println("${modifierText}Datatype: $name")

        val params = extractParams(datatype)

        val definitions = datatype.expr()

        val dataContainer = DatatypeObject(name, params, definitions)

        objects.add(dataContainer)
    }

    override fun visitTypeDefinition(type: TypeDefinitionContext) {
        val name = type.name.text

        val modifierText = type.modifier?.text.orEmpty()
        println("${modifierText}Type: $name")

        val params = extractParams(type)

        val definition = type.definition

        val dataContainer = TypeObject(type, name, params, definition)

        objects.add(dataContainer)
    }
}
