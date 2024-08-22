package trufflegen.main

import trufflegen.antlr.CBSLexer
import trufflegen.antlr.CBSParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import trufflegen.antlr.CBSBaseVisitor
import java.io.File


fun main() {
    val directory = File("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")

    if (!directory.exists() || !directory.isDirectory) {
        println("Invalid directory: ${directory.absolutePath}")
    }

    println("Declaration Phase")
    directory.walkTopDown().filter { isFileOfType(it, "cbs") }.forEach { file ->
        println("Processing file: ${file.absolutePath}")
        val declarationVisitor = DeclarationVisitor()
        processFile(file, declarationVisitor)
    }

    println("Definition Phase")
    directory.walkTopDown().filter { isFileOfType(it, "cbs") }.forEach { file ->
        println("Processing file: ${file.absolutePath}")
        val definitionVisitor = DefinitionVisitor()
        processFile(file, definitionVisitor)
    }
}

private fun <T> processFile(file: File, visitor: CBSBaseVisitor<T>): T {
    val input = CharStreams.fromFileName(file.absolutePath)
    val lexer = CBSLexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = CBSParser(tokens)
    val tree = parser.root()
    return visitor.visit(tree)
}
