package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class ObjectBuilderVisitor : CBSBaseVisitor<List<ObjectDataContainer>>() {
    private val objects = mutableListOf<ObjectDataContainer>()

    private val primitives = mutableListOf<String>()

    private fun extractArgs(funcon: ParseTree): List<ExprContext> {
        return when (funcon) {
            is FunconExpressionContext -> {
                when (val args = funcon.funconExpr().args()) {
                    is NoArgsContext -> emptyList()
                    is SingleArgsContext -> listOf(args.expr())
                    is MultipleArgsContext -> args.exprs().expr()
                    else -> throw IllegalArgumentException("Unexpected args type: ${args::class.simpleName}")
                }
            }

            is FunconObjContext -> funcon.params()?.param()?.map { it.value ?: it.type } ?: emptyList()

            else -> throw IllegalStateException("Unexpected funcon type: ${funcon::class.simpleName}")
        }
    }

    private fun rewrite(
        ruleDef: ParseTree, rewriteExpr: ParseTree, params: List<Param>
    ): String {
        val args = when (ruleDef) {
            is FunconExpressionContext -> extractArgs(ruleDef)
            is FunconObjContext -> extractArgs(ruleDef)
            else -> throw IllegalStateException("Unexpected rule definition ${ruleDef::class.simpleName}")
        }

        val rewriteVisitor = RewriteVisitor(params, args)
        println("Before: ${rewriteExpr.text}")
        val rewritten = rewriteVisitor.visit(rewriteExpr)
        println("After: $rewritten")
        return rewritten
    }

    private fun buildRules(funconParams: List<Param>, ruleDefs: List<RuleObjContext>): String {
        var rewritten = ""
        ruleDefs.forEach { rule ->
            when (rule) {
                is TransitionRuleContext -> {
                    val premises = rule.premises().premise().toList()
                    premises.forEach { premise ->
                        when (premise) {
                            is RewritePremiseContext -> {}
                            is StepPremiseContext -> {}
                            is MutableEntityPremiseContext -> {}
                            is BooleanPremiseContext -> {}
                            is TypePremiseContext -> {}
                        }
                    }

                    when (val conclusion = rule.conclusion) {
                        is RewritePremiseContext -> {}
                        is StepPremiseContext -> {}
                        is MutableEntityPremiseContext -> {}
                        is BooleanPremiseContext -> {}
                        is TypePremiseContext -> {}
                    }
                }

                is RewriteRuleContext -> when (val premise = rule.premise()) {
                    is RewritePremiseContext -> {
                        if (premise.lhs !is FunconExpressionContext) {
                            throw IllegalArgumentException("lhs is somehow not a funcon")
                        }
                        println("\nrule: ${premise.text}")
                        rewritten = rewrite(premise.lhs, premise.rewritesTo, funconParams)
                    }

                    is StepPremiseContext -> {}
                    is MutableEntityPremiseContext -> {}
                    is BooleanPremiseContext -> {}
                    is TypePremiseContext -> {}
                }
            }
        }
        return rewritten
    }

    override fun visitFunconObject(funcon: FunconObjectContext): List<ObjectDataContainer> {
        try {
            val funconDef = funcon.funconObj()
            val name = funconDef.name.text
            println("\nFuncon: $name")

            val params = funconDef.params()?.param()?.mapIndexed { index, param ->
                Param(index, Value(param.value), ParamType(param.type))
            } ?: emptyList()

            val returns = ReturnType(funconDef.returnType)
            val aliases = funcon.aliasObj().map { alias -> alias.name.text }

            val content = funconDef.rewritesTo?.let { rewriteExpr ->
                rewrite(funconDef, rewriteExpr, params)
            } ?: run {
                buildRules(params, funcon.ruleObj())
            }

            val dataContainer = FunconObjectData(name, params, content, returns, aliases)

            objects.add(dataContainer)
        } catch (e: Exception) {
            println("Failed to build Funcon: ${funcon.funconObj().name.text}. Error: ${e.message}")
        }

        return objects
    }

    override fun visitDatatypeObj(datatype: DatatypeObjContext): List<ObjectDataContainer> {
        try {
            val name = datatype.name.text
            println("\nDatatype: $name")

            val datatypePrimitives = mutableListOf<String>()
            val datatypeComposites = mutableListOf<ExprContext>()

            // Uncomment and implement processing if needed
            // datatype.datatypeDefs().expr().map { def ->
            //     println(def::class)
            // }

            // val dataContainer = DatatypeObjectData(name, datatypePrimitives, datatypeComposites)

            // objects.add(dataContainer)

        } catch (e: Exception) {
            println("Failed to build Datatype: ${datatype.name.text}. Error: ${e.message}")
        }

        return objects
    }
}
