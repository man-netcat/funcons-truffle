package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class CBSFile(val name: String, val root: RootContext) : CBSBaseVisitor<Unit>() {
    internal val objects = mutableMapOf<String, Object?>()
    internal var metavariables: Map<String, String> = emptyMap()

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
                key to buildTypeRewrite(Type(def.definition))
            }
        }.toMap()
    }

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        val name = funcon.name.text
        val params = extractParams(funcon)
        val returns = Type(funcon.returnType)
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
        val params = extractParams(datatype)
        val operator = datatype.op?.text ?: ""
        val aliases = datatype.aliasDefinition()
        val definitions = extractAndOrExprs(datatype.definition)

        when (operator) {
            "<:" -> {
                val datatypeDataContainer =
                    SupertypeDatatypeObject(name, datatype, params, definitions, aliases, metavariables)
                objects[name] = datatypeDataContainer
            }

            "::=" -> {
                val datatypeDataContainer = AlgebraicDatatypeObject(name, datatype, params, aliases, metavariables)
                objects[name] = datatypeDataContainer

                definitions.forEach { funcon ->
                    when (funcon) {
                        is FunconExpressionContext -> {
                            val name = funcon.name.text
                            val params = argsToParams(funcon)
                            val dataContainer =
                                DatatypeFunconObject(name, funcon, params, datatypeDataContainer, metavariables)
                            objects[name] = dataContainer
                        }

                        is SetExpressionContext -> {
                            // TODO what fresh hell is this
                        }

                        else -> throw DetailedException(
                            "Unexpected expression type encountered: ${funcon::class.simpleName}, with text: '${funcon.text}'"
                        )
                    }
                }
            }
        }
    }

    override fun visitTypeDefinition(type: TypeDefinitionContext) {
        val name = type.name.text
        val params = extractParams(type)
        val operator = type.op?.text ?: ""
        val definitions = if (type.definition != null) extractAndOrExprs(type.definition) else emptyList()
        val aliases = type.aliasDefinition()
        val builtin = type.modifier != null
        val dataContainer = TypeObject(name, type, params, definitions, aliases, metavariables)

        objects[name] = dataContainer
    }

    override fun visitControlEntityDefinition(controlEntity: ControlEntityDefinitionContext) {
        val name = controlEntity.name.text
        val params = extractParams(controlEntity)
        val polarity = controlEntity.polarity?.text
        val aliases = controlEntity.aliasDefinition()
        val dataContainer = ControlEntityObject(name, controlEntity, params, polarity, aliases, metavariables)

        objects[name] = dataContainer
    }

    override fun visitMutableEntityDefinition(mutableEntity: MutableEntityDefinitionContext) {
        if (mutableEntity.lhsName.text != mutableEntity.rhsName.text) throw DetailedException(
            "mutableEntity lhsName ${mutableEntity.lhsName.text} is not equal to rhsName ${mutableEntity.rhsName.text}"
        )
        val name = mutableEntity.lhsName.text
        val params = extractParams(mutableEntity)
        val aliases = mutableEntity.aliasDefinition()
        val dataContainer = MutableEntityObject(name, mutableEntity, params, aliases, metavariables)

        objects[name] = dataContainer
    }

    override fun visitContextualEntityDefinition(contextualEntity: ContextualEntityDefinitionContext) {
        val name = contextualEntity.name.text
        val params = extractParams(contextualEntity)
        val aliases = contextualEntity.aliasDefinition()
        val dataContainer = ContextualEntityObject(name, contextualEntity, params, aliases, metavariables)

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
