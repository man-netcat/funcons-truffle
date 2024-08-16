package com.trufflegen.main

import CBSLanguage
import com.oracle.truffle.api.nodes.RootNode
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

class FCTParser(private val code: String) {

    fun parseFile(): RootNode {
        // Step 1: Use ANTLR to parse the code into a parse tree
        val inputStream = CharStreams.fromString(code)
        val lexer = FCTLexer(inputStream)
        val tokens = CommonTokenStream(lexer)
        val parser = FCTParser(tokens)

        // Assuming you have a top-level rule called "program"
        val tree = parser.parse()

        // Step 2: Convert the parse tree to a Truffle AST
        // For simplicity, we return a RootNode that does nothing for now
        return CBSRootNode(CBSLanguage, tree)
    }
}