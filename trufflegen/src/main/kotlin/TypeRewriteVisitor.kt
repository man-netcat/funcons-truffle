package main

import antlr.CBSBaseVisitor
import antlr.CBSParser.*

class TypeRewriteVisitor(
    private val type: Type, private val nullable: Boolean
) : CBSBaseVisitor<String>() {
    var nestedValue = 0

    override fun visitFunconExpression(ctx: FunconExpressionContext): String {
        val funconName = toClassName(ctx.name.text)
        val args = makeArgList(ctx.args())
        val argStr = args.joinToString { arg -> if (arg.text == "_") "*" else visit(arg) }
        return funconName + if (!argStr.isEmpty()) "<$argStr>" else ""
    }

    override fun visitSuffixExpression(ctx: SuffixExpressionContext): String {
        val baseType = visit(ctx.expr())
        return when (val op = ctx.op.text) {
            "?" -> if (nullable) "$baseType?" else baseType
            "*", "+" -> if (type.isParam && nestedValue == 0) {
                nestedValue++
                baseType
            } else "Array<$baseType>"

            else -> throw DetailedException("Unexpected operator: $op, full context: ${ctx.text}")
        }
    }

    override fun visitTupleExpression(ctx: TupleExpressionContext): String {
        if (ctx.exprs() == null) return "EmptySequenceNode"

        val clsName = when (val tupleLength = ctx.exprs().expr().size) {
            0 -> "EmptySequenceNode"
            1 -> "Tuple1Node"
            2 -> "Tuple2Node"
            3 -> "TupleNode"
            else -> throw DetailedException("Unexpected tuple length: $tupleLength")
        }
        return clsName + "<" + ctx.exprs().expr().joinToString { visit(it) } + ">"
    }

    override fun visitListExpression(ctx: ListExpressionContext): String? {
        if (ctx.exprs() == null) return "Unit"
        return "ListNode<${ctx.exprs().expr().joinToString { visit(it) }}>"
    }

    override fun visitBinaryComputesExpression(ctx: BinaryComputesExpressionContext): String =
        "(" + visit(ctx.lhs) + ") -> " + visit(ctx.rhs)

    override fun visitOrExpression(ctx: OrExpressionContext): String {
        return when (ctx.rhs.text) {
            ctx.rhs.text -> visit(ctx.lhs) + "?"
            else -> throw DetailedException("Unexpected return type: ${ctx.text}")
        }
    }

    override fun visitPowerExpression(ctx: PowerExpressionContext): String {
        return if (type.isParam && nestedValue == 0) {
            nestedValue++
            visit(ctx.operand)
        } else "Array<${visit(ctx.operand)}>"
    }

    override fun visitVariable(ctx: VariableContext): String = processVariable(ctx)
    override fun visitNestedExpression(ctx: NestedExpressionContext): String = visit(ctx.expr())
    override fun visitUnaryComputesExpression(ctx: UnaryComputesExpressionContext): String = visit(ctx.expr())
    override fun visitNumber(ctx: NumberContext): String = ctx.text
    override fun visitTypeExpression(ctx: TypeExpressionContext): String {
        return visit(ctx.type)
    }

    override fun visitComplementExpression(ctx: ComplementExpressionContext): String {
        return visit(ctx.expr())
    }
}