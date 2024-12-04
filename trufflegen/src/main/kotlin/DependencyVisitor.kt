package main

import antlr.CBSBaseVisitor
import antlr.CBSParser.*

class DependencyVisitor(
    private val index: MutableSet<String>,
    internal var dependencies: MutableSet<String>,
    internal var builtins: MutableSet<String>,
) : CBSBaseVisitor<Unit>() {
    internal var currentObj: String? = null

    override fun visitFunconDefinition(funcon: FunconDefinitionContext) {
        val objName = funcon.name.text
        if (objName !in index && objName !in dependencies) return
        if (funcon.modifier != null) { // either builtin or auxiliary
            builtins.add(objName)
            return
        }
        currentObj = objName
        visitChildren(funcon)
    }

    override fun visitDatatypeDefinition(datatype: DatatypeDefinitionContext) {
        val objName = datatype.name.text
        if (objName !in index && objName !in dependencies) return
        if (datatype.modifier != null) {
            builtins.add(objName)
            return
        }
        currentObj = objName
        visitChildren(datatype)
    }

    override fun visitTypeDefinition(type: TypeDefinitionContext) {
        val objName = type.name.text
        if (objName !in index && objName !in dependencies) return
        if (type.modifier != null) {
            builtins.add(objName)
            return
        }
        currentObj = objName
        visitChildren(type)
    }

    override fun visitFunconExpression(expr: FunconExpressionContext) {
        if (currentObj == null) return
        val depName = expr.name.text
        if (depName !in index) {
//            if (depName !in dependencies) println("Found dependency $depName of $currentObj")
            dependencies.add(depName)
        }
        return visitChildren(expr)
    }

    override fun visitLabel(label: LabelContext) {
        if (currentObj == null) return
        val depName = label.name.text
        if (depName !in index) {
//            if (depName !in dependencies) println("Found dependency $depName of $currentObj")
            dependencies.add(depName)
        }
        return visitChildren(label)
    }
}