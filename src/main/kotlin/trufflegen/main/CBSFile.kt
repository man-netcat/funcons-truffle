package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class CBSFile(val name: String, val root: RootContext, private val index: Set<String>) : CBSBaseVisitor<Unit>() {
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
        if (name !in index) {
            return
        }

        val params = extractParams(funcon)

        val returns = ReturnType(funcon.returnType)
        val aliases = funcon.aliasDefinition()

        val dataContainer = if (funcon.rewritesTo != null) {
            val rewritesTo = funcon.rewritesTo
            FunconObjectWithRewrite(funcon, name, params, rewritesTo, returns, aliases, metavariables)
        } else {
            val rules: List<RuleDefinitionContext> = funcon.ruleDefinition()
            FunconObjectWithRules(funcon, name, params, rules, returns, aliases, metavariables)
        }

        objects.add(dataContainer)
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext) {
        val name = datatype.name.text
        if (name !in index) return

        val params = extractParams(datatype)

        tailrec fun extractAndExprs(
            expr: ExprContext, definitions: List<ExprContext> = emptyList(),
        ): List<ExprContext> = when (expr) {
            is OrExpressionContext -> extractAndExprs(expr.lhs, definitions + expr.rhs)
            else -> definitions + expr
        }

        val definitions = extractAndExprs(datatype.definition)

        val aliases = datatype.aliasDefinition()

        val dataContainer = DatatypeObject(datatype, name, params, definitions, aliases, metavariables)

        objects.add(dataContainer)
    }

    override fun visitTypeDefinition(type: TypeDefinitionContext) {
        val name = type.name.text
        if (name !in index) return

        val definition = type.definition

        val aliases = type.aliasDefinition()

        val dataContainer = TypeObject(type, name, definition, aliases, metavariables)

        objects.add(dataContainer)
    }

    fun generateCode(builtins: MutableSet<String>): String {
        val imports = listOf(
            "fctruffle.main.*",
            "com.oracle.truffle.api.frame.VirtualFrame",
            "com.oracle.truffle.api.nodes.Node.Child",
            "com.oracle.truffle.api.nodes.Node.Children"
        ).joinToString("\n") { "import $it" }

        val code = objects.joinToString("\n\n") { obj ->
            if (obj.name in builtins) {
                println("\nGenerating code for builtin ${obj::class.simpleName} ${obj.name} (File $name)")
                val code = obj.generateBuiltinTemplate()
                val aliasStr = obj.aliasStr()
                if (aliasStr.isNotBlank()) "$code\n\n$aliasStr" else code
            } else {
                println("\nGenerating code for ${obj::class.simpleName} ${obj.name} (File $name)")
                try {
                    val code = obj.generateCode()
                    val aliasStr = obj.aliasStr()
                    if (aliasStr.isNotBlank()) "$code\n\n$aliasStr" else code
                } catch (e: Exception) {
                    println(e)
                    ""
                }
            }
        }.trim()

        return "package fctruffle.generated\n\n$imports\n\n$code".trim()
    }
}
