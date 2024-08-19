package cbsgrammartester.main

import cbsgrammartester.antlr4.CBSLexer
import cbsgrammartester.antlr4.CBSParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: kotlin TestCBSKt <directory_path>")
        exitProcess(1)
    }

    val directoryPath = args[0]
    val directory = File(directoryPath)

    if (!directory.isDirectory) {
        println("The path provided is not a directory")
        exitProcess(1)
    }

    try {
        directory.walk().filter { it.isFile && it.extension == "cbs" }.forEach { file ->
            println("Processing file: ${file.absolutePath}")
            try {
                parseFile(file)
            } catch (e: IOException) {
                println("Failed to process file ${file.absolutePath}: ${e.message}")
            }
        }
    } catch (e: Exception) {
        println("An error occurred: ${e.message}")
        exitProcess(1)
    }
}

fun parseFile(file: File) {
    val input = CharStreams.fromFileName(file.absolutePath)
    val lexer = CBSLexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = CBSParser(tokens)

    // Replace `ruleName` with the starting rule of your grammar
    val tree: ParserRuleContext = parser.main()
    println("Parsed successfully: ${file.absolutePath}")
}
