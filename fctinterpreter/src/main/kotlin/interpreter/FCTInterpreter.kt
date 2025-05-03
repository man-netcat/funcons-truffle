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

    try {
        Interpreter.createContext(standardInArgs).use { context ->
            val result = Interpreter.evalFile(context, filePath)
            Interpreter.processResult(result)
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}

object Interpreter {
    fun createContext(args: Array<String> = emptyArray()): Context {
        return Context.newBuilder("fctlang")
            .allowAllAccess(true)
            .arguments("fctlang", args)
            .build()
    }

    fun evalFile(context: Context, filePath: Path): Value {
        val code = Files.readString(filePath)
        val source = Source.newBuilder("fctlang", code, filePath.pathString).build()
        return context.eval(source)
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
}
