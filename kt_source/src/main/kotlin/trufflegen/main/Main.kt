package trufflegen.main

import com.oracle.truffle.api.source.Source
import java.io.File

fun main(args: Array<String>) {
    // Check if the file path argument is provided
    if (args.isEmpty()) {
        println("Usage: <program> <path-to-fct-file>")
        return
    }

    // Get the file path from command-line arguments
    val filePath = args[0]

    // Check if the file exists
    val file = File(filePath)
    if (!file.exists()) {
        println("Error: File not found at '$filePath'")
        return
    }

    // Read the file contents
    val code = file.readText()

    // Create a Source object
    val source = Source.newBuilder(CBSLanguage.MIME_TYPE, code, filePath).build()

    // Create an instance of CBSLanguage
    val language = CBSLanguage()

    // Parse the source code to create a CallTarget
    val callTarget = language.parse(source)

    val result = callTarget.call()
    println("Execution result: $result")
}
