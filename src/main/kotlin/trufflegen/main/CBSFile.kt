package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class CBSFile(val name: String, val root: RootContext) : CBSBaseVisitor<Unit>() {
    private val metavariables = mutableMapOf<ExprContext, ExprContext>()

    internal val objects = mutableListOf<Object>()

    override fun visitMetavarDef(def: MetavarDefContext) {
        metavariables.putAll(def.variables.expr().mapNotNull { variable -> variable to def.definition }.toMap())
    }

    private fun extractParams(obj: ParseTree): List<Param> {
        val params = when (obj) {
            is FunconDefinitionContext -> obj.params()
            is DatatypeDefinitionContext -> obj.params()
            is TypeDefinitionContext -> obj.params()
            else -> throw DetailedException("Unexpected object: ${obj::class.simpleName}")
        }

        return params?.param()?.mapIndexed { paramIndex, param ->
            Param(paramIndex, param.value, param.type)
        } ?: emptyList()
    }

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        val name = funcon.name.text

        val params = extractParams(funcon)

        val returns = ReturnType(funcon.returnType)
        val aliases = funcon.aliasDefinition()

        val builtin = funcon.modifier != null

        val dataContainer = if (funcon.rewritesTo != null) {
            val rewritesTo = funcon.rewritesTo
            FunconObjectWithRewrite(name, funcon, params, rewritesTo, returns, aliases, metavariables, builtin)
        } else {
            val rules: List<RuleDefinitionContext> = funcon.ruleDefinition()
            FunconObjectWithRules(name, funcon, params, rules, returns, aliases, metavariables, builtin)
        }

        objects.add(dataContainer)
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext) {
        val name = datatype.name.text

        val params = extractParams(datatype)

        val definitions = extractAndOrExprs(datatype.definition)

        val aliases = datatype.aliasDefinition()

        val builtin = datatype.modifier != null

        val dataContainer = DatatypeObject(name, datatype, params, definitions, aliases, metavariables, builtin)

        objects.add(dataContainer)
    }

    override fun visitTypeDefinition(type: TypeDefinitionContext) {
        val name = type.name.text

        val params = extractParams(type)

        val definitions = if (type.definitions != null) extractAndOrExprs(type.definitions) else emptyList()

        val aliases = type.aliasDefinition()

        val builtin = type.modifier != null

        val dataContainer = TypeObject(name, params, type, definitions, aliases, metavariables, builtin)

        objects.add(dataContainer)
    }

    fun generateCode(): String {
        val imports = listOf(
            "fctruffle.main.*",
            "com.oracle.truffle.api.frame.VirtualFrame",
            "com.oracle.truffle.api.nodes.Node.Child",
            "com.oracle.truffle.api.nodes.Node.Children"
        ).joinToString("\n") { "import $it" }

        val code = objects.joinToString("\n\n") { obj ->
            println("\nGenerating code for ${obj::class.simpleName} ${obj.name} (File $name)")
            try {
                val code = obj.generateCode()
                val aliasStr = obj.aliasStr()
                if (aliasStr.isNotBlank()) "$code\n\n$aliasStr" else code
            } catch (e: StringNotFoundException) {
                println(e)
                ""
            } catch (e: EmptyConditionException) {
                println(e)
                ""
            }
        }.trim()

        return "package fctruffle.generated\n\n$imports\n\n$code".trim()
    }
}
