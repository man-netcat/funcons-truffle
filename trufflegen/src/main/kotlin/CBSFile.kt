package main

import cbs.CBSBaseVisitor
import cbs.CBSParser.*
import main.dataclasses.Type
import main.exceptions.DetailedException
import main.exceptions.EmptyConditionException
import main.exceptions.StringNotFoundException
import main.objects.*
import main.visitors.MetaVariableVisitor
import objects.FunconObject
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

class CBSFile(val name: String) : CBSBaseVisitor<Unit>() {
    internal val objects = mutableMapOf<String, Object?>()
    private var fileMetavariables: Set<Pair<ExprContext, ExprContext>> = emptySet()

    private fun identifyMetavariables(ctx: ParseTree): MutableSet<Pair<String, String>> {
        val visitor = MetaVariableVisitor(fileMetavariables)
        visitor.visit(ctx)
        return visitor.objectMetaVariables
    }

    private fun processAliases(aliases: List<AliasDefinitionContext>): List<String> {
        return aliases.mapNotNull { it.name.text }
    }

    private fun addObjectReference(obj: Object, name: String, aliases: List<String> = emptyList()) {
        objects[name] = obj
        aliases.forEach { alias -> objects[alias] = obj }
    }

    override fun visitMetavariablesDefinition(metavarDefs: MetavariablesDefinitionContext) {
        fileMetavariables = metavarDefs.metavarDef().flatMap { def ->
            def.variables.expr().mapNotNull { metaVar -> metaVar to def.supertype }
        }.filter { (varName, _) -> varName is VariableContext }.toSet()
    }

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        val name = funcon.name.text
        val params = extractParams(funcon)
        val returns = Type(funcon.returnType)
        val aliases = processAliases(funcon.aliasDefinition())
        val builtin = funcon.modifier != null
        val metaVariables = identifyMetavariables(funcon)

        val dataContainer = FunconObject(
            name,
            funcon,
            params,
            aliases,
            metaVariables,
            returns,
            builtin,
            rules = funcon.ruleDefinition(),
            rewritesTo = funcon.rewritesTo
        )

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
                            val datatypeFunconName = funcon.name.text
                            val datatypeFunconParams = argsToParams(funcon)
                            val dataContainer =
                                DatatypeFunconObject(
                                    datatypeFunconName,
                                    funcon,
                                    datatypeFunconParams,
                                    metaVariables,
                                    datatypeDataContainer
                                )
                            addObjectReference(dataContainer, datatypeFunconName)
                            datatypeDataContainer.definitions.add(dataContainer)
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
        val operator = type.op?.text
        val definitions = if (type.definition != null) extractAndOrExprs(type.definition) else emptyList()
        val aliases = processAliases(type.aliasDefinition())
        val builtin = type.modifier != null
        val metaVariables = identifyMetavariables(type)

        val dataContainer = TypeObject(name, type, params, metaVariables, aliases, definitions)

        addObjectReference(dataContainer, name, aliases)
    }

    private fun processEntityDefinition(
        context: ParserRuleContext, entityType: EntityType
    ) {
        val (name, aliasDefinition) = when (context) {
            is ControlEntityDefinitionContext -> context.name.text to context.aliasDefinition()
            is ContextualEntityDefinitionContext -> context.name.text to context.aliasDefinition()
            is MutableEntityDefinitionContext -> context.name.text to context.aliasDefinition()
            else -> throw DetailedException(
                "Unexpected context type encountered: ${context::class.simpleName}, with text: '${context.text}'"
            )
        }
        val params = extractParams(context)
        val aliases = processAliases(aliasDefinition)
        val metaVariables = identifyMetavariables(context)

        val dataContainer = EntityObject(name, context, params, aliases, metaVariables, entityType)

        addObjectReference(dataContainer, name, aliases)
    }

    override fun visitControlEntityDefinition(controlEntity: ControlEntityDefinitionContext) {
        processEntityDefinition(controlEntity, EntityType.CONTROL)
    }

    override fun visitMutableEntityDefinition(mutableEntity: MutableEntityDefinitionContext) {
        processEntityDefinition(mutableEntity, EntityType.MUTABLE)
    }

    override fun visitContextualEntityDefinition(contextualEntity: ContextualEntityDefinitionContext) {
        processEntityDefinition(contextualEntity, EntityType.CONTEXTUAL)
    }

    fun generateCode(generatedDependencies: MutableSet<Object>): String {
        val included = generatedDependencies.ifEmpty { globalObjects.values.toSet() }
        val toProcess = objects.values.distinct().filterNotNull().filter { obj -> obj in included }

        if (toProcess.isEmpty()) return ""

        val stringBuilder = StringBuilder()

        stringBuilder.appendLine("package generated")
        stringBuilder.appendLine()

        val imports = listOf(
            "generated.*",
            "interpreter.*",
            "com.oracle.truffle.api.frame.VirtualFrame",
            "com.oracle.truffle.api.nodes.Node.Child",
            "com.oracle.truffle.api.nodes.Node.Children"
        )
        imports.forEach { stringBuilder.appendLine("import $it") }
        stringBuilder.appendLine()

//        fileMetavariables.forEach { (varName, superType) ->
//            val varStr = rewriteType(Type(varName), nullable = false)
//            val superTypeStr = rewriteType(Type(superType), nullable = false)
//            stringBuilder.appendLine("typealias $varStr = $superTypeStr")
//        }
//        stringBuilder.appendLine()

        toProcess.forEach { obj ->
            println("\nGenerating code for ${obj::class.simpleName} ${obj.name} (File $name)")
            try {
                val code = obj.code
                val aliasStr = obj.aliasStr
                stringBuilder.appendLine()
                stringBuilder.appendLine(code)
                if (aliasStr.isNotBlank()) {
                    stringBuilder.appendLine()
                    stringBuilder.appendLine(aliasStr)
                }
            } catch (e: StringNotFoundException) {
                println(e)
            } catch (e: EmptyConditionException) {
                println(e)
            }
        }

        return stringBuilder.toString().trim()
    }
}
