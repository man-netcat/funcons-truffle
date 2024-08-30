package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class DefinitionBuilderVisitor : CBSBaseVisitor<List<DefinitionDataContainer>>() {
    private val objects = mutableListOf<DefinitionDataContainer>()

    private val primitives = mutableListOf<String>()

    private lateinit var funconParams: List<Param>

    private fun extractArgs(funcon: ParseTree): List<ExprContext> {
        return when (funcon) {
            is FunconExpressionContext -> {
                when (val args = funcon.args()) {
                    is NoArgsContext -> emptyList()
                    is SingleArgsContext -> listOf(args.expr())
                    is MultipleArgsContext -> args.exprs().expr()
                    is ListIndexExpressionContext -> args.indices.expr()
                    else -> throw Exception("Unexpected args type: ${args::class.simpleName}")
                }
            }

            is FunconDefinitionContext -> funcon.params()?.param()?.map { it.value ?: it.type } ?: emptyList()

            else -> throw Exception("Unexpected funcon type: ${funcon::class.simpleName}")
        }
    }

    private fun booleanExpr(boolPremise: BooleanPremiseContext, params: List<Param>) {
        val visitor = RewriteVisitor(params, params.map { param -> param.value.value })
        val lhs = visitor.visit(boolPremise.lhs)
        val rhs = visitor.visit(boolPremise.rhs)
    }

    private fun buildRewrite(
        ruleDef: ParseTree, rewriteExpr: ParseTree, params: List<Param>
    ): String {
        if (ruleDef !is FunconExpressionContext && ruleDef !is FunconDefinitionContext) {
            throw Exception("Unexpected rule definition ${ruleDef::class.simpleName}")
        }
        val args = extractArgs(ruleDef)
        val rewriteVisitor = RewriteVisitor(params, args)
        println("Before: ${rewriteExpr.text}")
        val rewritten = rewriteVisitor.visit(rewriteExpr)
        println("After: $rewritten")
        return rewritten
    }

    private fun buildTransition(rule: TransitionRuleContext) {
        val premises = rule.premises().premise().toList()
        when (val conclusion = rule.conclusion) {
            is RewritePremiseContext -> {
                println("\nrewrite conclusion: ${conclusion.text}")
                val rewritten = buildRewrite(conclusion.lhs, conclusion.rewritesTo, funconParams)
            }

            is StepPremiseContext -> {
                val stepExpr = conclusion.stepExpr()
                val rewritten = buildRewrite(stepExpr.lhs, stepExpr.rewritesTo, funconParams)

            }

            is MutableEntityPremiseContext -> {}
            else -> throw Exception("Unexpected conclusion type: ${conclusion::class.simpleName}")
        }

        premises.forEach { premise ->
            when (premise) {
                is RewritePremiseContext -> {}
                is StepPremiseContext -> {}
                is MutableEntityPremiseContext -> {}
                is BooleanPremiseContext -> {}
                is TypePremiseContext -> {}
            }
        }
    }

    private fun buildRules(ruleDefs: List<RuleDefinitionContext>): String {
        var rewritten = ""
        ruleDefs.forEach { rule ->
            when (rule) {
                is TransitionRuleContext -> buildTransition(rule)

                is RewriteRuleContext -> when (val premise = rule.premise()) {
                    is RewritePremiseContext -> {
                        println("\nrewrite premise: ${premise.text}")
                        rewritten = buildRewrite(premise.lhs, premise.rewritesTo, funconParams)
                    }

                    is StepPremiseContext -> {}
                    is MutableEntityPremiseContext -> {}
                    else -> throw Exception("Unexpected premise type: ${premise::class.simpleName}")
                }
            }
        }
        return rewritten
    }

    override fun visitFunconDefinition(funcon: FunconDefinitionContext): List<DefinitionDataContainer> {
        try {
            val name = funcon.name.text

            val modifierText = funcon.modifier?.text.orEmpty()
            val funconStr = """
                ${modifierText}Funcon: $name
                Signature: ${funcon.text}
            """.trimIndent()

            println("\n$funconStr")

            funconParams = funcon.params()?.param()?.mapIndexed { index, param ->
                Param(index, Value(param.value), ParamType(param.type))
            } ?: emptyList()

            val returns = ReturnType(funcon.returnType)
            val aliases = funcon.aliasDefinition().map { alias -> alias.name.text }

            val content = if (funcon.rewritesTo != null) {
                buildRewrite(funcon, funcon.rewritesTo, funconParams)
            } else {
                buildRules(funcon.ruleDefinition())
            }

            val dataContainer = FunconDefinitionData(name, funconParams, content, returns, aliases)

            objects.add(dataContainer)
        } catch (e: Exception) {
            println("Failed to build Funcon: ${funcon.name.text}. Error: ${e.message}")
        }

        return objects
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext): List<DefinitionDataContainer> {
        try {
            val name = datatype.name.text

            val datatypeStr = "${datatype.modifier?.text ?: ""}Datatype: $name".trimStart()
            println("\n$datatypeStr")

            val datatypePrimitives = mutableListOf<String>()
            val datatypeComposites = mutableListOf<ExprContext>()

            // Uncomment and implement processing if needed
            // datatype.datatypeDefs().expr().map { def ->
            //     println(def::class)
            // }

            // val dataContainer = DatatypeDefinitionData(name, datatypePrimitives, datatypeComposites)

            // objects.add(dataContainer)

        } catch (e: Exception) {
            println("Failed to build Datatype: ${datatype.name.text}. Error: ${e.message}")
        }

        return objects
    }
}
