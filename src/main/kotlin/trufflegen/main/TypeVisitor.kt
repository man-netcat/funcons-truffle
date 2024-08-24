package trufflegen.main

import org.antlr.v4.runtime.tree.RuleNode
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class TypeVisitor : CBSBaseVisitor<TypeData>() {
    private val typeData = TypeData()

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext): TypeData {
        when (suffixExpr.op.type) {
            STAR -> typeData.stars++
            PLUS -> typeData.pluses++
            QMARK -> typeData.qmarks++
            POWN -> typeData.powns++
            else -> throw IllegalArgumentException("Unknown suffix operator: ${suffixExpr.op.text}")
        }

        return super.visitSuffixExpression(suffixExpr)
    }

    override fun visitUnaryComputesExpression(ctx: UnaryComputesExpressionContext?): TypeData {
        typeData.computes++
        return super.visitUnaryComputesExpression(ctx)
    }

    override fun visitChildren(node: RuleNode?): TypeData {
        return super.visitChildren(node) ?: typeData
    }
}
