package main.visitors

import cbs.CBSBaseVisitor
import cbs.CBSParser.*
import main.dataclasses.Type
import main.exceptions.DetailedException
import org.antlr.v4.runtime.tree.RuleNode

class MetaVariableVisitor(
    private val fileMetaVariables: Set<Pair<ExprContext, ExprContext>>
) : CBSBaseVisitor<Unit>() {

    val objectMetaVariables: MutableSet<Pair<String, String>> = mutableSetOf()

    override fun visitChildren(node: RuleNode) {
        fileMetaVariables.forEach { (metaVariable, superType) ->
            if (metaVariable.text == node.text) {
                val (metaVariableStr, superTypeStr) = when (metaVariable) {
                    is SuffixExpressionContext -> {
                        val castSuperType = (superType as? SuffixExpressionContext)?.operand ?: throw DetailedException(
                            "Expected SuffixExpressionContext for superType, found ${superType::class.simpleName}"
                        )
                        Pair(
                            Type(metaVariable.operand).rewrite(),
                            Type(castSuperType).rewrite()
                        )
                    }

                    is VariableContext -> Pair(
                        Type(metaVariable).rewrite(),
                        Type(superType).rewrite()
                    )

                    else -> throw DetailedException("Unexpected metaVariable type: ${metaVariable::class.simpleName}, ${metaVariable.text}")
                }


                objectMetaVariables.add(metaVariableStr to superTypeStr)
            }
        }

        super.visitChildren(node)
    }
}