package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class DefinitionVisitor : CBSBaseVisitor<Unit>() {
    override fun visitTypeExpression(typeExpr: TypeExpressionContext) {
        val text = "value: ${typeExpr.value.text}, type: ${typeExpr.type.text}"

        println(text)

        super.visitTypeExpression(typeExpr)
    }

    override fun visitStepExpr(stepExpr: StepExprContext) {
        val text = "rewritesTo: ${stepExpr.rewritesTo.text}"

        println(text)

        super.visitStepExpr(stepExpr)
    }

    override fun visitRewritePremise(premise: RewritePremiseContext) {
        val text = "lhs: ${premise.lhs.text}, rewritesTo: ${premise.rewritesTo.text}"

        println(text)

        super.visitRewritePremise(premise)
    }
}
