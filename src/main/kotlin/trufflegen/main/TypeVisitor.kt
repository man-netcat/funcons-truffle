package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class TypeVisitor : CBSBaseVisitor<Unit>() {
    private val typeData = TypeData()

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext) {
        when (suffixExpr.op.type) {
            STAR -> typeData.stars++
            PLUS -> typeData.pluses++
            QMARK -> typeData.qmarks++
            POWN -> typeData.powns++
        }

        super.visitSuffixExpression(suffixExpr)
    }

    override fun visitUnaryComputesExpression(ctx: UnaryComputesExpressionContext) {
        typeData.computes++
        super.visitUnaryComputesExpression(ctx)
    }

    override fun visitFunconExpression(ctx: FunconExpressionContext?) {
        super.visitFunconExpression(ctx)
    }

    fun getTypeData(): TypeData {
        return typeData
    }
}
