package interpreter

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.pathString

object FCTInterpreter {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Usage: Interpreter <file_path> [args...]")
            return
        }

        val filePath = Paths.get(args[0]).toAbsolutePath().normalize()
        val standardInArgs = args.drop(1).toTypedArray()

        try {
            createContext(standardInArgs).use { context ->
                val result = evalFile(context, filePath)
                processResult(result, filePath)
            }
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    fun createContext(args: Array<String> = emptyArray()): Context =
        Context.newBuilder("fctlang")
            .allowAllAccess(true)
            .arguments("fctlang", args)
            .build()

    fun evalFile(context: Context, filePath: Path): Value {
        val code = Files.readString(filePath)
        val source = Source.newBuilder("fctlang", code, filePath.pathString).build()
        return context.eval(source)
    }

    fun processResult(result: Value, filePath: Path) {
        val isFct = filePath.extension == "fct"
        val resultTerm = result.getArrayElement(0)
        val store = result.getArrayElement(1)

        val standardOut = (2 until result.arraySize).map { i ->
            val raw = result.getArrayElement(i).asString()
            if (isFct) raw.removeSurrounding("\"").replace("\\n", "\n") else raw
        }

        if (isFct) {
            print(standardOut.joinToString(""))
        } else {
            println("results:")
            println("result-term: $resultTerm")
            println("store: $store")
            println("standard-out: $standardOut")
        }
    }
}