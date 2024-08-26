package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.icecream.IceCream.ic
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class ObjectBuilderVisitor : CBSBaseVisitor<List<ObjectDataContainer>>() {
    private val objects = mutableListOf<ObjectDataContainer>()

    private val primitives = mutableListOf<String>()

    private fun extractArgs(ctx: FunconExpressionContext): List<ExprContext> {
        val funcon = ctx.funconExpr()
        return when (val args = funcon.args()) {
            is NoArgsContext -> emptyList()
            is SingleArgsContext -> listOf(args.expr())
            is MultipleArgsContext -> args.exprs().expr()
            else -> throw IllegalArgumentException()
        }
    }

    private fun buildRules(funconParams: List<Param>, ruleDefs: List<RuleObjContext>): String {
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
                        val args = extractArgs(premise.lhs as FunconExpressionContext)
                        val rewriteVisitor = RewriteVisitor(funconParams, args)
                        ic(premise.rewritesTo.text)
                        val rewritten = rewriteVisitor.visit(premise.rewritesTo)
                        ic(rewritten)
                        assert(rewritten != null)
                    }

                    is StepPremiseContext -> {}
                    is MutableEntityPremiseContext -> {}
                    is BooleanPremiseContext -> {}
                    is TypePremiseContext -> {}
                }
            }
        }
        return ""
    }


    override fun visitFunconObject(funcon: FunconObjectContext): List<ObjectDataContainer> {
        val funconDef = funcon.funconObj()
        val name = funconDef.name.text
        println("\nFuncon: $name")

        val params = funconDef.params()?.param()?.mapIndexed { index, param ->
            Param(index, Value(param.value), ParamType(param.type))
        } ?: emptyList()

        val rules = buildRules(params, funcon.ruleObj())

        val returns = ReturnType(funconDef.returnType)

        val aliases = funcon.aliasObj().map { alias -> alias.name.text }

        val dataContainer = FunconObjectData(name, params, returns, aliases)

        objects.add(dataContainer)

        return objects
    }

    override fun visitDatatypeObj(datatype: DatatypeObjContext): List<ObjectDataContainer> {
        val name = datatype.name.text
        println("\nDatatype: $name")

        val datatypePrimitives = mutableListOf<String>()
        val datatypeComposites = mutableListOf<ExprContext>()

//        datatype.datatypeDefs().expr().map { def ->
//            if (def !is FunconExpressionContext) {
//
//            }
//            when (def) {
//                is FunconExpressionContext -> {
//                    primitives.add(def.text)
//                    println("Primitive: ${def.text}")
//                    datatypePrimitives.add(def.text)
//                }
//
//                is CompositeContext -> {
//                    datatypeComposites.add(def.expr())
//                }
//
//                else -> throw IllegalStateException()
//            }
//        }

        val dataContainer = DatatypeObjectData(name, datatypePrimitives, datatypeComposites)

        objects.add(dataContainer)

        return objects
    }

    override fun visitChildren(node: RuleNode): List<ObjectDataContainer> {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child is ParseTree) {
                child.accept(this)
            }
        }
        return objects
    }
}
