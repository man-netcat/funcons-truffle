package main

import org.antlr.v4.runtime.tree.ParseTree
import antlr.CBSBaseVisitor
import antlr.CBSParser.*
import main.exceptions.*
import main.visitors.MetaVariableVisitor
import main.objects.*

class CBSFile(val name: String, val root: RootContext) : CBSBaseVisitor<Unit>() {
    internal val objects = mutableMapOf<String, Object?>()
    internal var fileMetavariables: List<Pair<ExprContext, ExprContext>> = emptyList()

    fun identifyMetavariables(ctx: ParseTree): MutableSet<Pair<String, String>> {
        val visitor = MetaVariableVisitor(fileMetavariables)
        visitor.visit(ctx)
        return visitor.objectMetaVariables
    }

    fun processAliases(aliases: List<AliasDefinitionContext>): List<String> {
        return aliases.map { it.name.text }
    }

    fun addObjectReference(obj: Object, name: String, aliases: List<String> = emptyList()) {
        objects[name] = obj
        aliases.forEach { alias -> objects[alias] = obj }
    }

    override fun visitMetavariablesDefinition(metavarDefs: MetavariablesDefinitionContext) {
        fileMetavariables = metavarDefs.metavarDef().flatMap { def ->
            def.variables.expr().mapNotNull { metaVar -> metaVar to def.supertype }
        }
    }

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        val name = funcon.name.text
        val params = extractParams(funcon)
        val returns = Type(funcon.returnType)
        val aliases = processAliases(funcon.aliasDefinition())
        val builtin = funcon.modifier != null
        val metaVariables = identifyMetavariables(funcon)

        val dataContainer = if (funcon.rewritesTo != null) {
            val rewritesTo = funcon.rewritesTo
            FunconObjectWithRewrite(name, funcon, params, rewritesTo, returns, aliases, builtin, metaVariables)
        } else if (!funcon.ruleDefinition().isEmpty()) {
            val rules: List<RuleDefinitionContext> = funcon.ruleDefinition()
            FunconObjectWithRules(name, funcon, params, rules, returns, aliases, builtin, metaVariables)
        } else {
            FunconObjectWithoutRules(name, funcon, params, returns, aliases, builtin, metaVariables)
        }

        addObjectReference(dataContainer, name, aliases)
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext) {
        val name = datatype.name.text
        val params = extractParams(datatype)
        val operator = datatype.op?.text ?: ""
        val aliases = processAliases(datatype.aliasDefinition())
        val definitions = extractAndOrExprs(datatype.definition)
        val metaVariables = identifyMetavariables(datatype)

        when (operator) {
            "<:" -> {
                val datatypeDataContainer =
                    SupertypeDatatypeObject(name, datatype, params, definitions, aliases, metaVariables)
                addObjectReference(datatypeDataContainer, name, aliases)

            }

            "::=" -> {
                val datatypeDataContainer = AlgebraicDatatypeObject(name, datatype, params, aliases, metaVariables)
                addObjectReference(datatypeDataContainer, name, aliases)


                definitions.forEach { funcon ->
                    when (funcon) {
                        is FunconExpressionContext -> {
                            val name = funcon.name.text
                            val params = argsToParams(funcon)
                            val dataContainer =
                                DatatypeFunconObject(name, funcon, params, datatypeDataContainer, metaVariables)
                            addObjectReference(dataContainer, name)
                        }

                        is SetExpressionContext -> {
                            // TODO: In the case one of the definitions is a set expression, make it the supertype of the parent
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
        val aliases = processAliases(type.aliasDefinition())
        val builtin = type.modifier != null
        val metaVariables = identifyMetavariables(type)

        val dataContainer = TypeObject(name, type, params, definitions, aliases, metaVariables)

        addObjectReference(dataContainer, name, aliases)
    }

    override fun visitControlEntityDefinition(controlEntity: ControlEntityDefinitionContext) {
        val name = controlEntity.name.text
        val params = extractParams(controlEntity)
        val polarity = controlEntity.polarity?.text
        val aliases = processAliases(controlEntity.aliasDefinition())
        val metaVariables = identifyMetavariables(controlEntity)

        val dataContainer = ControlEntityObject(name, controlEntity, params, polarity, aliases, metaVariables)

        addObjectReference(dataContainer, name, aliases)
    }

    override fun visitMutableEntityDefinition(mutableEntity: MutableEntityDefinitionContext) {
        if (mutableEntity.lhsName.text != mutableEntity.rhsName.text) throw DetailedException(
            "mutableEntity lhsName ${mutableEntity.lhsName.text} is not equal to rhsName ${mutableEntity.rhsName.text}"
        )
        val name = mutableEntity.lhsName.text
        val params = extractParams(mutableEntity)
        val aliases = processAliases(mutableEntity.aliasDefinition())
        val metaVariables = identifyMetavariables(mutableEntity)

        val dataContainer = MutableEntityObject(name, mutableEntity, params, aliases, metaVariables)

        addObjectReference(dataContainer, name, aliases)
    }

    override fun visitContextualEntityDefinition(contextualEntity: ContextualEntityDefinitionContext) {
        val name = contextualEntity.name.text
        val params = extractParams(contextualEntity)
        val aliases = processAliases(contextualEntity.aliasDefinition())
        val metaVariables = identifyMetavariables(contextualEntity)

        val dataContainer = ContextualEntityObject(name, contextualEntity, params, aliases, metaVariables)

        addObjectReference(dataContainer, name, aliases)
    }

    fun generateCode(): String {
        val imports = listOf(
            "main.*",
            "com.oracle.truffle.api.frame.VirtualFrame",
        ).joinToString("\n") { "import $it" }

        val code = objects.values.distinct().filterNotNull().joinToString("\n\n") { obj ->
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

        return "package generated\n\n$imports\n\n$code".trim()
    }
}
