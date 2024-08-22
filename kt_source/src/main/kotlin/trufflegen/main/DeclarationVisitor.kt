package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class DeclarationVisitor : CBSBaseVisitor<Unit>() {

    override fun visitFunconDef(funcon: FunconDefContext?) {
        if (funcon == null) return

        val name = funcon.name.text

        val returns = funcon.returnType.text

        val paramsText = funcon.params?.text ?: "No parameters"

        val text = "Funcon Name: $name, params: $paramsText, Returns: $returns"

        println(text)

        return
    }
}
