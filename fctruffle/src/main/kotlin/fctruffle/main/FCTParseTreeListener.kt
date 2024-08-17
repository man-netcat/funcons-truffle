package fctruffle.main

import com.oracle.truffle.api.nodes.RootNode
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeListener
import org.antlr.v4.runtime.tree.TerminalNode

class FCTParseTreeListener : ParseTreeListener {
    private lateinit var rootNode: RootNode

    // Implement methods for entering and exiting various parse tree nodes
    override fun visitTerminal(node: TerminalNode?) {
        // Handle terminal nodes if necessary
    }

    override fun visitErrorNode(node: ErrorNode?) {
        // Handle error nodes if necessary
    }

    override fun enterEveryRule(ctx: ParserRuleContext) {
        // Handle rule entry
    }

    override fun exitEveryRule(ctx: ParserRuleContext) {
        // Handle rule exit
        // Build your AST here based on the context
    }

    fun getRootNode(): RootNode {
        return rootNode
    }
}
