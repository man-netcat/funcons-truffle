package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.DatatypeDefContext
import trufflegen.antlr.CBSParser.FunconDefContext

class DataCollectionVisitor : CBSBaseVisitor<List<ObjectDataContainer>>() {
    private val results = mutableListOf<ObjectDataContainer>()

    override fun visitFunconDef(funcon: FunconDefContext): List<ObjectDataContainer> {
        val name = funcon.name.text
        println("Funcon: $name")

        // Map the params and include their index
        val params = funcon.params()?.param()?.mapIndexed { index, param ->
            Param(index, Value(param.value), ParamType(param.type))
        } ?: emptyList()

        val returns = ReturnType(funcon.returnType)
        val dataContainer = FunconObjectData(name, params, returns)

        results.add(dataContainer)

        return results
    }

    override fun visitDatatypeDef(datatype: DatatypeDefContext): List<ObjectDataContainer> {
        val name = datatype.name.text
        println("datatype: $name")

        val definition = datatype.definition.text
        val dataContainer = DatatypeObjectData(name, definition)

        results.add(dataContainer)

        return results
    }

    override fun visitChildren(node: RuleNode): List<ObjectDataContainer> {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child is ParseTree) {
                child.accept(this)
            }
        }
        return results
    }
}
