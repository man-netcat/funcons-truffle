package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

abstract class Type(val expr: ExprContext) : CBSBaseVisitor<Unit>() {
    private var computes: Int = 0
    var qmarks: Int = 0
    var pluses: Int = 0
    var stars: Int = 0
    val text: String = expr.text

    init {
        this.visit(expr)
    }

    val typeCategory: TypeCategory
        get() = when {
            pluses > 0 -> TypeCategory.PLUS
            stars > 0 -> TypeCategory.STAR
            qmarks > 0 -> TypeCategory.QMARK
            else -> TypeCategory.SINGLE
        }

    val isLazy: Boolean
        get() = computes > 1

    abstract val isVararg: Boolean
    abstract val isArray: Boolean

    override fun visitSuffixExpression(suffixExpr: SuffixExpressionContext) {
        when (suffixExpr.op.type) {
            STAR -> stars++
            PLUS -> pluses++
            QMARK -> qmarks++
        }

        super.visitSuffixExpression(suffixExpr)
    }

    override fun visitUnaryComputesExpression(ctx: UnaryComputesExpressionContext) {
        computes++
        super.visitUnaryComputesExpression(ctx)
    }

    override fun visitFunconExpression(ctx: FunconExpressionContext?) {}
    override fun visitTupleExpression(ctx: TupleExpressionContext?) {}
    override fun visitListExpression(ctx: ListExpressionContext?) {}
    override fun visitSetExpression(ctx: SetExpressionContext?) {}
    override fun visitMapExpression(ctx: MapExpressionContext?) {}
}
