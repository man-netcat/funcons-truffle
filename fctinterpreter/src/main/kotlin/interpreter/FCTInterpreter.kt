package interpreter

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.pathString

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: FCTInterpreter <file_path>")
        return
    }

    val filePath = Paths.get(args[0])
    val code = try {
        Files.readString(filePath)
    } catch (e: Exception) {
        println("Error reading file: ${e.message}")
        return
    }

    print("standard-out: ")
    val context = Context.newBuilder("fctlang").allowAllAccess(true).build()
    val source = Source.newBuilder("fctlang", code, filePath.pathString).build()
    try {
        val result = context.eval(source)
        println("\nresult-term: $result")
    } catch (e: Exception) {
        println("Error during execution: ${e.message}")
    }
}

