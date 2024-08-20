package trufflegen.main

import trufflegen.antlr4.CBSBaseVisitor
import trufflegen.antlr4.CBSParser

class CBSVisitor : CBSBaseVisitor<CBSNode?>() {
    override fun visitFunconDef(ctx: CBSParser.FunconDefContext?): CBSNode? {
        if (ctx == null) return null

        val name = ctx.IDENTIFIER().text
        println("Funcon Name: $name")

        val exprsContext = ctx.exprs()
        exprsContext?.expr()?.map { expr ->
            println(expr.text)
        }

        return null
    }
}
