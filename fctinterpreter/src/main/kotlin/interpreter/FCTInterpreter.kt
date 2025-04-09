package interpreter

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.pathString

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: FCTInterpreter <file_path> [args...]")
        return
    }

    val filePath = Paths.get(args[0])
    val standardInArgs = args.drop(1).toTypedArray()
    val code = try {
        Files.readString(filePath)
    } catch (e: Exception) {
        println("Error reading file: ${e.message}")
        return
    }

    val context = Context
        .newBuilder("fctlang")
        .arguments("fctlang", standardInArgs)
        .allowAllAccess(true)
        .build()

    val source = Source
        .newBuilder("fctlang", code, filePath.pathString)
        .build()

    val result: Value = context.eval(source)
    if (result.hasArrayElements()) {
        val resultTerm = result.getArrayElement(0)
        val standardOut = (1 until result.arraySize).map { i ->
            result.getArrayElement(i)
        }
        println("result-term: $resultTerm")
        println("standard-out: $standardOut")
    }
}