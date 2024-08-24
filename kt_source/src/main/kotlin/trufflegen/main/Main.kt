package trufflegen.main

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import trufflegen.antlr.CBSLexer
import trufflegen.antlr.CBSParser
import java.io.File

fun main() {
    val directory = File("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")

    if (!directory.exists() || !directory.isDirectory) {
        println("Invalid directory: ${directory.path}")
        return
    }

    // First pass: parse each file and store the parse tree
    val fileTreeMap = mutableMapOf<File, CBSParser.RootContext?>()
    directory.walkTopDown().filter { isFileOfType(it, "cbs") }.forEach { file ->
        println("Generating parse tree for file: ${file.name}")
        val input = CharStreams.fromFileName(file.absolutePath)
        val lexer = CBSLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = CBSParser(tokens)
        val tree = parser.root()
        fileTreeMap[file] = tree
    }

    // Second pass: collect data for funcon/type declarations
    val dataCollectionVisitor = DataCollectionVisitor()
    val combinedData = mutableMapOf<String, ObjectDataContainer>()
    fileTreeMap.forEach { (file, tree) ->
        println("\nProcessing data for file: ${file.name}")
        val data = tree?.let { dataCollectionVisitor.visit(it) }
        if (data != null) {
            combinedData.putAll(data)
        }
    }

    // Third pass: write code for files
    combinedData.forEach { (objectName, obj) ->
        println("\nProcessing object: $objectName")
        val code = obj.generateCode()
        println(code)
    }
}
