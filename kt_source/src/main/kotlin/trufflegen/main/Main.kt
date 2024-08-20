package trufflegen.main

import trufflegen.antlr4.CBSLexer
import trufflegen.antlr4.CBSParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.Trees
import java.io.File

fun main() {
    val directory = File("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")

    if (directory.exists() && directory.isDirectory) {
        processDirectory(directory)
    } else {
        println("Invalid directory: ${directory.absolutePath}")
    }
}

private fun processDirectory(directory: File) {
    directory.walkTopDown().filter { it.isFile && it.extension == "cbs" }.forEach { file ->
        println("Processing file: ${file.absolutePath}")
        processFile(file)
    }
}

private fun processFile(file: File) {
    try {
        val input = CharStreams.fromFileName(file.absolutePath)
        val lexer = CBSLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = CBSParser(tokens)

        val tree = parser.root()
        println("Parse tree for ${file.name}:")
        printTree(tree, parser)

    } catch (e: Exception) {
        println("Error processing file ${file.absolutePath}: ${e.message}")
    }
}

private fun printTree(tree: ParseTree, parser: CBSParser, indentation: String = "") {
    // Print the current node with the provided indentation
    val nodeText = Trees.getNodeText(tree, parser)
    println("$indentation$nodeText")

    // Recursively process each child node with increased indentation
    for (i in 0 until tree.childCount) {
        printTree(tree.getChild(i), parser, "$indentation  ")
    }
}
