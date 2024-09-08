package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

abstract class Type(type: ExprContext) : CBSBaseVisitor<Unit>() {
    private var computes: Int = 0
    private var qmarks: Int = 0
    var pluses: Int = 0
    var stars: Int = 0
    private var powns: Int = 0
    val text: String = type.text

    init {
        this.visit(type)
//        println("pluses: $pluses, stars: $stars, qmarks: $qmarks, powns: $powns")
    }

    val typeCategory: TypeCategory
        get() = when {
            pluses > 0 -> TypeCategory.PLUS
            stars > 0 -> TypeCategory.STAR
            powns > 0 -> TypeCategory.POWN
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
            POWN -> powns++
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
