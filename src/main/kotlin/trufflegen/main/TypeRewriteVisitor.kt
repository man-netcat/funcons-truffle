package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class TypeRewriteVisitor(private val type: Type) : CBSBaseVisitor<String>() {
    override fun visitFunconExpression(ctx: FunconExpressionContext): String {
        val funconName = ctx.name.text
        return when (funconName) {
            "maps" -> {
                val args = (ctx.args() as MultipleArgsContext).exprs().expr()
                assert(args.size == 2)
                val argStr = args.joinToString { visit(it) }
                "Map<$argStr>"
            }

            "sets", "set" -> {
                val args = (ctx.args() as MultipleArgsContext).exprs().expr()
                assert(args.size == 1)
                "Set<${visit(args[0])}>"
            }

            "lists" -> {
                val args = (ctx.args() as MultipleArgsContext).exprs().expr()
                assert(args.size == 1)
                "List<${visit(args[0])}>"
            }

            "tuples" -> {
                val args = (ctx.args() as MultipleArgsContext).exprs().expr()
                val cls = when (args.size) {
                    2 -> "Tuple"
                    3 -> "Triple"
                    else -> throw DetailedException("Unexpected arg size: ${args.size}")
                }
                val argStr = args.joinToString { visit(it) }
                "$cls<$argStr>"
            }

            else -> when (ctx.args()) {
                is NoArgsContext -> toClassName(funconName)
                else -> throw DetailedException("Unexpected funcon ${ctx::class.simpleName}: ${ctx.text}")
            }

        }
    }

    override fun visitVariable(ctx: VariableContext): String = ctx.varname.text

    override fun visitSuffixExpression(ctx: SuffixExpressionContext): String {
        return when (val op = ctx.op.text) {
            "?" -> visit(ctx.expr()) + "?"
            "*" -> visit(ctx.expr()) + {
                when (type) {
                    is ReturnType -> "List<${visit(ctx.expr())}>"
                    is ParamType -> {
                        if (type.isArray) "List<${visit(ctx.expr())}>" else visit(ctx.expr())
                    }
                }
            }

            else -> throw DetailedException("Unexpected operator: $op, ${ctx.text}")
        }
    }

    override fun visitNestedExpression(ctx: NestedExpressionContext): String? {
        return visit(ctx.expr())
    }

//    override fun visitChildren(node: RuleNode): String {
//        println("${node::class.simpleName}: ${node.text}")
//        return super.visitChildren(node)
//    }
}