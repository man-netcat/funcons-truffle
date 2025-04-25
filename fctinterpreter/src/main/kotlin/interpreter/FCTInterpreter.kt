package interpreter

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: FCTInterpreter <file_path> [args...]")
        return
    }

    val filePath = Paths.get(args[0])
    val standardInArgs = args.drop(1).toTypedArray()
    val result = try {
        evalFile(filePath, standardInArgs)
    } catch (e: Exception) {
        println("Error: ${e.message}")
        return
    }

    processResult(result)
}

fun evalFile(filePath: Path, args: Array<String> = emptyArray()): Value {
    val code = Files.readString(filePath)
    val context = Context.newBuilder("fctlang")
        .arguments("fctlang", args)
        .allowAllAccess(true)
        .build()
    val source = Source.newBuilder("fctlang", code, filePath.pathString).build()
    return context.use { it.eval(source) }
}

fun processResult(result: Value) {
    val resultTerm = result.getArrayElement(0)
    val store = result.getArrayElement(1)
    val standardOut = (2 until result.arraySize).joinToString(",") { i ->
        result.getArrayElement(i).toString()
    }
    println("results:")
    println("result-term: $resultTerm")
    println("store: $store")
    println("standard-out: [$standardOut]")
}