package trufflegen.main

import trufflegen.antlr4.CBSLexer
import trufflegen.antlr4.CBSParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
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

        val visitor = CBSVisitor()

        visitor.visit(tree)
    } catch (e: Exception) {
        println("Error processing file ${file.absolutePath}: ${e.message}")
    }
}