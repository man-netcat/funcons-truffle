package trufflegen.main

import org.antlr.v4.runtime.tree.RuleNode
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class MetaVariableVisitor(private val fileMetaVariables: List<Pair<ExprContext, ExprContext>>) :
    CBSBaseVisitor<Unit>() {

    val objectMetaVariables: MutableSet<Pair<String, String>> = mutableSetOf()

    override fun visitChildren(node: RuleNode) {
        fileMetaVariables.forEach { (metaVariable, superType) ->
            if (metaVariable.text == node.text) {
                val (metaVariableStr, superTypeStr) = when (metaVariable) {
                    is SuffixExpressionContext -> {
                        val castSuperType = (superType as? SuffixExpressionContext)?.operand ?: throw DetailedException(
                            "Expected SuffixExpressionContext for superType, found ${superType::class.simpleName}"
                        )
                        Pair(buildTypeRewrite(Type(metaVariable.operand)), buildTypeRewrite(Type(castSuperType)))
                    }

                    is VariableContext, is VariableStepContext -> Pair(
                        buildTypeRewrite(Type(metaVariable)),
                        buildTypeRewrite(Type(superType))
                    )

                    else -> throw DetailedException("Unexpected metaVariable type: ${metaVariable::class.simpleName}, ${metaVariable.text}")
                }


                objectMetaVariables.add(metaVariableStr to superTypeStr)
            }
        }

        super.visitChildren(node)
    }
}