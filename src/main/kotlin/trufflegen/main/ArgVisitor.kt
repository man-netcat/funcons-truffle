package trufflegen.main

import org.antlr.v4.runtime.tree.RuleNode
import trufflegen.antlr.CBSBaseVisitor

class ArgVisitor(private val str: String) : CBSBaseVisitor<Boolean>() {
    override fun visitChildren(node: RuleNode): Boolean {
        if (node.text == str) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child?.accept(this) == true) return true
        }

        return false
    }
}
