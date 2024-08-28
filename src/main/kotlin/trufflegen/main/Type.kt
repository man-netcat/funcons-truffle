package trufflegen.main

import trufflegen.antlr.CBSParser.ExprContext

abstract class Type(type: ExprContext) {
    val text: String = type.text
    val visitor = TypeVisitor()
    val typeData: TypeData
        get() {
            return visitor.getTypeData()
        }

    init {
        visitor.visit(type)
    }

    val typeCategory: TypeCategory
        get() = when {
            typeData.pluses > 0 -> TypeCategory.PLUS
            typeData.stars > 0 -> TypeCategory.STAR
            typeData.powns > 0 -> TypeCategory.POWN
            typeData.qmarks > 0 -> TypeCategory.QMARK
            else -> TypeCategory.SINGLE
        }

    val isLazy: Boolean
        get() = typeData.computes > 1

    abstract val isVararg: Boolean
    abstract val isArray: Boolean
}
