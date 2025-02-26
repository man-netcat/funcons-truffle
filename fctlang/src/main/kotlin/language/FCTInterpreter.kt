package language

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: FCTInterpreter <file_path>")
        return
    }

    val filePath = args[0]
    val code = try {
        Files.readString(Paths.get(filePath))
    } catch (e: Exception) {
        println("Error reading file: ${e.message}")
        return
    }

    // Create a Truffle Context and evaluate the source
    val context = Context.newBuilder().build()
    val source = Source.newBuilder("fctlang", code, filePath).build()
    try {
        val result = context.eval(source)
        println("Execution Result: $result")
    } catch (e: Exception) {
        println("Error during execution: ${e.message}")
    }
}