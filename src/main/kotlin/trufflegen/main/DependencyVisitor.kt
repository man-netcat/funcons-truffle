package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class DependencyVisitor(private val index: MutableSet<String>) : CBSBaseVisitor<Unit>() {
    internal val dependencies = mutableSetOf<String>()

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        if (funcon.name.text in index) {
            visitChildren(funcon)
        }
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext) {
        if (datatype.name.text in index) {
            visitChildren(datatype)
        }
    }

    override fun visitTypeDefinition(type: TypeDefinitionContext) {
        if (type.name.text in index) {
            visitChildren(type)
        }
    }

    override fun visitFunconExpression(expr: FunconExpressionContext) {
        dependencies.add(expr.name.text)
        return visitChildren(expr)
    }

    override fun visitAction(action: ActionContext) {
        dependencies.add(action.name.text)
        return visitChildren(action)
    }
}