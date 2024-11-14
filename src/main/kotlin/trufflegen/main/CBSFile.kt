package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class CBSFile(val name: String, val root: RootContext) : CBSBaseVisitor<Unit>() {
    internal val objects = mutableMapOf<String, Object?>()
    internal var metavariables: Map<String, String> = emptyMap()

    private fun extractParams(params: ParamsContext?): List<Param> {
        return params?.param()?.mapIndexed { paramIndex, param ->
            Param(paramIndex, param.value, param.type)
        } ?: emptyList()
    }

    override fun visitMetavariablesDefinition(metavarDefs: MetavariablesDefinitionContext) {
        metavariables = metavarDefs.metavarDef().flatMap { def ->
            def.variables.expr().mapNotNull { metaVar ->
                val key = when (metaVar) {
                    is VariableContext -> metaVar.text
                    is VariableStepContext -> makeVariableStepName(metaVar)
                    is SuffixExpressionContext -> metaVar.operand.text
                    else -> throw DetailedException(
                        "Unexpected metaVar type encountered: ${metaVar::class.simpleName}, with text: '${metaVar.text}'"
                    )
                }
                key to buildTypeRewrite(ReturnType(def.definition))
            }
        }.toMap()
    }

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        val name = funcon.name.text
        val params = extractParams(funcon.params())
        val returns = ReturnType(funcon.returnType)
        val aliases = funcon.aliasDefinition()
        val builtin = funcon.modifier != null
        val dataContainer = if (funcon.rewritesTo != null) {
            val rewritesTo = funcon.rewritesTo
            FunconObjectWithRewrite(name, funcon, params, rewritesTo, returns, aliases, builtin, metavariables)
        } else if (!funcon.ruleDefinition().isEmpty()) {
            val rules: List<RuleDefinitionContext> = funcon.ruleDefinition()
            FunconObjectWithRules(name, funcon, params, rules, returns, aliases, builtin, metavariables)
        } else {
            FunconObjectWithoutRules(name, funcon, params, returns, aliases, builtin, metavariables)
        }

        objects[name] = dataContainer
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext) {
        val name = datatype.name.text
        val params = extractParams(datatype.params())
        val operator = datatype.op?.text ?: ""
        val definitions = extractAndOrExprs(datatype.definition)
        val aliases = datatype.aliasDefinition()
        val builtin = datatype.modifier != null
        val dataContainer =
            DatatypeObject(name, datatype, params, operator, definitions, aliases, builtin, metavariables)

        objects[name] = dataContainer
    }

    override fun visitTypeDefinition(type: TypeDefinitionContext) {
        val name = type.name.text
        val params = extractParams(type.params())
        val operator = type.op?.text ?: ""
        val definitions = if (type.definitions != null) extractAndOrExprs(type.definitions) else emptyList()
        val aliases = type.aliasDefinition()
        val builtin = type.modifier != null
        val dataContainer = TypeObject(name, type, params, operator, definitions, aliases, builtin, metavariables)

        objects[name] = dataContainer
    }

    fun generateCode(): String {
        val imports = listOf(
            "fctruffle.main.*",
            "com.oracle.truffle.api.frame.VirtualFrame",
            "com.oracle.truffle.api.nodes.Node.Child",
            "com.oracle.truffle.api.nodes.Node.Children"
        ).joinToString("\n") { "import $it" }

        val code = objects.values.filterNotNull().joinToString("\n\n") { obj ->
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
