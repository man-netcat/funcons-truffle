package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

abstract class Type(val expr: ExprContext?) : CBSBaseVisitor<Unit>() {
    private var computes: Int = 0
    var qmarks: Int = 0
    var pluses: Int = 0
    var stars: Int = 0
    var powers: Int = 0
    var complement: Boolean = false
    val text: String = expr?.text ?: "null"

    init {
        this.visit(expr)
    }

    val annotation: String
        get() = if (isVararg) "@Children private vararg val" else "@Child private val"

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

    override fun visitPowerExpression(ctx: PowerExpressionContext?) {
        powers++
        return super.visitPowerExpression(ctx)
    }

    override fun visitComplementExpression(ctx: ComplementExpressionContext?) {
        complement = true
        return super.visitComplementExpression(ctx)
    }

//    override fun visitFunconExpression(ctx: FunconExpressionContext?) {}
//    override fun visitTupleExpression(ctx: TupleExpressionContext?) {}
//    override fun visitListExpression(ctx: ListExpressionContext?) {}
//    override fun visitSetExpression(ctx: SetExpressionContext?) {}
//    override fun visitMapExpression(ctx: MapExpressionContext?) {}
}
