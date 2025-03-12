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

class CBSFile(private val fileName: String) : CBSBaseVisitor<Unit>() {
    val objects = mutableMapOf<String, Object?>()
    private var fileMetavariables: Set<Pair<ExprContext, ExprContext>> = emptySet()

    private fun identifyMetavariables(ctx: ParseTree): MutableSet<Pair<String, String>> {
        val visitor = MetaVariableVisitor(fileMetavariables)
        visitor.visit(ctx)
        return visitor.objectMetaVariables
    }

    private fun addObjectReference(obj: Object) {
        objects[obj.name] = obj
        obj.aliases.forEach { alias -> objects[alias] = obj }
    }

    override fun visitMetavariablesDefinition(ctx: MetavariablesDefinitionContext) {
        fileMetavariables = ctx.metavarDef().flatMap { def ->
            def.variables.expr().mapNotNull { metaVar -> metaVar to def.supertype }
        }.filter { (varName, _) -> varName is VariableContext }.toSet()
    }

    override fun visitFunconDefinition(ctx: FunconDefinitionContext) {
        val returns = Type(ctx.returnType)
        val builtin = ctx.modifier != null
        val metaVariables = identifyMetavariables(ctx)

        val dataContainer = FunconObject(
            ctx,
            metaVariables,
            returns,
            builtin,
            rules = ctx.ruleDefinition(),
            rewritesTo = ctx.rewritesTo
        )

        addObjectReference(dataContainer)
    }

    override fun visitDatatypeDefinition(ctx: DatatypeDefinitionContext) {
        val operator = ctx.op?.text ?: ""
        val definitions = extractAndOrExprs(ctx.definition)
        val metaVariables = identifyMetavariables(ctx)

        when (operator) {
            "<:" -> {
                val datatypeDataContainer =
                    SupertypeDatatypeObject(ctx, metaVariables, definitions)
                addObjectReference(datatypeDataContainer)

            }

            "::=" -> {
                val datatypeDataContainer = AlgebraicDatatypeObject(ctx, metaVariables)
                addObjectReference(datatypeDataContainer)


                definitions.forEach { funcon ->
                    when (funcon) {
                        is FunconExpressionContext -> {
                            val dataContainer =
                                DatatypeFunconObject(
                                    funcon,
                                    metaVariables,
                                    datatypeDataContainer
                                )
                            addObjectReference(dataContainer)
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

    override fun visitTypeDefinition(ctx: TypeDefinitionContext) {
        val metaVariables = identifyMetavariables(ctx)
        val dataContainer = TypeObject(ctx, metaVariables)
        addObjectReference(dataContainer)
    }

    private fun processEntityDefinition(ctx: ParserRuleContext, entityType: EntityType) {
        val metaVariables = identifyMetavariables(ctx)
        val dataContainer = EntityObject(ctx, metaVariables, entityType)
        addObjectReference(dataContainer)
    }

    override fun visitControlEntityDefinition(ctx: ControlEntityDefinitionContext) {
        val entityType = if (ctx.polarity != null) {
            when (ctx.polarity.text) {
                "?" -> EntityType.INPUT
                "!" -> EntityType.OUTPUT
                else -> throw DetailedException("Unexpected polarity encountered: ${ctx.polarity.text}")
            }
        } else EntityType.CONTROL
        processEntityDefinition(ctx, entityType)
    }

    override fun visitMutableEntityDefinition(ctx: MutableEntityDefinitionContext) {
        processEntityDefinition(ctx, EntityType.MUTABLE)
    }

    override fun visitContextualEntityDefinition(ctx: ContextualEntityDefinitionContext) {
        processEntityDefinition(ctx, EntityType.CONTEXTUAL)
    }

    fun generateCode(generatedDependencies: MutableSet<Object>): String? {
        val included = generatedDependencies.ifEmpty { globalObjects.values.toSet() }
        val toProcess = objects.values.distinct().filterNotNull().filter { obj -> obj in included }

        if (toProcess.isEmpty()) return null

        val stringBuilder = StringBuilder()

        stringBuilder.appendLine("package generated")
        stringBuilder.appendLine()

        val imports = listOf(
            "generated.*",
            "language.*",
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
            println("\nGenerating code for ${obj::class.simpleName} ${obj.name} (File $fileName)")
            try {
                val code = obj.code
                val aliasStr = obj.aliasStr
                stringBuilder.appendLine()
                stringBuilder.appendLine(code)
                if (aliasStr.isNotBlank()) {
                    stringBuilder.appendLine()
//                    stringBuilder.appendLine(aliasStr)
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
