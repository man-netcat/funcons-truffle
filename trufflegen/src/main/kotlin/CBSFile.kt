package main

import cbs.CBSBaseVisitor
import cbs.CBSParser.*
import main.dataclasses.Type
import main.exceptions.DetailedException
import main.objects.*
import objects.DatatypeFunconObject
import objects.FunconObject
import org.antlr.v4.runtime.ParserRuleContext

class CBSFile(private val fileName: String) : CBSBaseVisitor<Unit>() {
    val objects = mutableMapOf<String, Object>()
    private var fileMetavariables: Set<Pair<ExprContext, ExprContext>> = emptySet()

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

        val dataContainer = FunconObject(
            ctx,
            rules = ctx.ruleDefinition(),
            term = ctx.rewritesTo,
            fileMetavariables
        )

        addObjectReference(dataContainer)
    }

    override fun visitDatatypeDefinition(ctx: DatatypeDefinitionContext) {
        val operator = ctx.op?.text ?: ""

        when (operator) {
            "<:" -> {
                val datatypeDataContainer = SupertypeDatatypeObject(ctx, fileMetavariables)
                addObjectReference(datatypeDataContainer)

            }

            "::=" -> {
                val definitions = extractAndOrExprs(ctx.definition)
                val datatypeDataContainer = AlgebraicDatatypeObject(ctx, fileMetavariables)
                addObjectReference(datatypeDataContainer)


                definitions.filter { def -> def is FunconExpressionContext }
                    .map { funcon ->
                        funcon as FunconExpressionContext
                        val dataContainer =
                            DatatypeFunconObject(
                                funcon,
                                datatypeDataContainer,
                                fileMetavariables
                            )
                        addObjectReference(dataContainer)
                        datatypeDataContainer.definitions.add(dataContainer)
                    }
            }
        }
    }

    override fun visitTypeDefinition(ctx: TypeDefinitionContext) {
        val dataContainer = TypeObject(ctx, fileMetavariables)
        addObjectReference(dataContainer)
    }

    private fun processEntityDefinition(ctx: ParserRuleContext, entityType: EntityType) {
        val dataContainer = EntityObject(ctx, fileMetavariables)
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
        val toProcess =
            objects.values.distinct().filter { obj -> obj in included }

        if (toProcess.isEmpty()) return null

        val stringBuilder = StringBuilder()

        stringBuilder.appendLine("package generated")
        stringBuilder.appendLine()

        val imports = listOf(
            "language.*",
            "builtin.*",
            "com.oracle.truffle.api.frame.VirtualFrame",
        )
        imports.forEach { stringBuilder.appendLine("import $it") }
        stringBuilder.appendLine()

//        fileMetavariables.forEach { (varName, superType) ->
//            val varStr = rewriteType(Type(varName), nullable = false)
//            val superTypeStr = rewriteType(Type(superType), nullable = false)
//            stringBuilder.appendLine("typealias $varStr = $superTypeStr")
//        }
//        stringBuilder.appendLine()

        for (obj in toProcess) {
            if (obj is EntityObject) continue
            println("\nGenerating code for ${obj::class.simpleName} ${obj.name} (File $fileName)")
            //            try {
            val code = obj.makeCode()
            stringBuilder.appendLine()
            stringBuilder.appendLine(code)
            if (obj is AlgebraicDatatypeObject && obj.elementInBody.isNotEmpty()) {
                stringBuilder.appendLine()
                stringBuilder.appendLine(obj.elementInBody)
            } else if (obj is TypeObject && obj.elementInBody.isNotEmpty()) {
                stringBuilder.appendLine()
                stringBuilder.appendLine(obj.elementInBody)
            }

            //            } catch (e: StringNotFoundException) {
            //                println(e)
            //            } catch (e: EmptyConditionException) {
            //                println(e)
            //            }
        }

        return stringBuilder.toString().trim()
    }
}
