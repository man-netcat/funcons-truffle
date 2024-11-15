package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class TypeRewriteVisitor(private val type: Type, private val nullable: Boolean) : CBSBaseVisitor<String>() {
    var nestedValue = 0
    var complement = false

    override fun visitFunconExpression(ctx: FunconExpressionContext): String {
        val funconName = toClassName(ctx.name.text)
        val args = when (val args = ctx.args()) {
            is NoArgsContext -> emptyList()
            is SingleArgsContext -> listOf(visit(args.expr()))
            is ListIndexExpressionContext -> args.exprs().expr().map { visit(it) }
            is MultipleArgsContext -> args.exprs().expr().map { visit(it) }
            else -> throw DetailedException("Unexpected arg type: ${args::class.simpleName}")
        }
        val argStr = args.filter { arg -> arg != "_" }.joinToString()
        return funconName + if (!argStr.isEmpty()) "<$argStr>" else ""
//        TODO: Could be useful for hardcoding types
//        return when (funconName) {
//            "map" -> {
//                val args = (ctx.args() as MultipleArgsContext).exprs().expr()
//                assert(args.size == 2)
//                val argStr = args.joinToString { visit(it) }
//                "Map<$argStr>"
//            }
//
//            "set" -> {
//                val args = (ctx.args() as MultipleArgsContext).exprs().expr()
//                assert(args.size == 1)
//                "Set<${visit(args[0])}>"
//            }
//
//            "list" -> {
//                val args = (ctx.args() as MultipleArgsContext).exprs().expr()
//                assert(args.size == 1)
//                "List<${visit(args[0])}>"
//            }
//
//            "tuple" -> {
//                val args = (ctx.args() as MultipleArgsContext).exprs().expr()
//                val cls = when (args.size) {
//                    2 -> "Tuple"
//                    3 -> "Triple"
//                    else -> throw DetailedException("Unexpected arg size: ${args.size}")
//                }
//                val argStr = args.joinToString { visit(it) }
//                "$cls<$argStr>"
//            }
//
//            else -> when (ctx.args()) {
//                is NoArgsContext -> toClassName(funconName)
//                else -> throw DetailedException("Unexpected funcon ${ctx::class.simpleName}: ${ctx.text}")
//            }
//    }
    }

    override fun visitSuffixExpression(ctx: SuffixExpressionContext): String {
        return when (val op = ctx.op.text) {
            "?" -> visit(ctx.expr()) + if (nullable) "?" else ""
            "*", "+" -> when (type) {
                is ReturnType -> "ListNode<${visit(ctx.expr())}>"
                is ParamType -> if (nestedValue == 0) {
                    nestedValue++
                    visit(ctx.expr())
                } else "ListNode<${visit(ctx.expr())}>"

                else -> throw DetailedException("Unexpected type: ${ctx::class.simpleName}")
            }

            else -> throw DetailedException("Unexpected operator: $op, ${ctx.text}")
        }
    }

    override fun visitTupleExpression(ctx: TupleExpressionContext): String {
        if (ctx.exprs() == null) return "Unit"

        val clsName = when (val tupleLength = ctx.exprs().expr().size) {
            2 -> "Tuple"
            3 -> "Triple"
            else -> throw DetailedException("Unexpected tuple length: $tupleLength")
        }
        return clsName + "<" + ctx.exprs().expr().joinToString { visit(it) } + ">"
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
        return when (type) {
            is ReturnType -> "ListNode<${visit(ctx.operand)}>"
            is ParamType -> if (nestedValue == 0) {
                nestedValue++
                visit(ctx.operand)
            } else "ListNode<${visit(ctx.operand)}>"

            else -> throw DetailedException("Unexpected type: ${ctx::class.simpleName}")
        }
    }

    override fun visitVariable(ctx: VariableContext): String = ctx.varname.text
    override fun visitVariableStep(ctx: VariableStepContext): String = ctx.varname.text + "p".repeat(ctx.squote().size)
    override fun visitNestedExpression(ctx: NestedExpressionContext): String = visit(ctx.expr())
    override fun visitUnaryComputesExpression(ctx: UnaryComputesExpressionContext): String = visit(ctx.expr())
    override fun visitNumber(ctx: NumberContext): String = ctx.text
    override fun visitTypeExpression(ctx: TypeExpressionContext): String {
        return visit(ctx.type)
    }

    override fun visitComplementExpression(ctx: ComplementExpressionContext): String {
        complement = true
        return visit(ctx.expr())
    }
}