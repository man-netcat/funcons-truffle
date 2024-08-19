package trufflegen.main

import trufflegen.antlr4.CBSLexer
import trufflegen.antlr4.CBSParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import org.icecream.IceCream.ic

fun main() {
    val directory = File("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")

    directory.listFiles { _, name -> name.endsWith(".cbs") }?.forEach { file ->
        println("Processing file: ${file.absolutePath}")
        processFile(file)
    } ?: println("No .cbs files found in the directory.")
}

private fun processFile(file: File) {
    try {
        val input = CharStreams.fromFileName(file.absolutePath)
        val lexer = CBSLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = CBSParser(tokens)

        val tree = parser.root()
        ic(tree)

        println("Parse tree for ${file.name}: $tree")

    } catch (e: Exception) {
        println("Error processing file ${file.absolutePath}: ${e.message}")
    }
}
