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

    fun processResult(result: Value, filePath: Path) {        val isFct = filePath.extension == "fct"
        val resultTerm = result.getArrayElement(0)
        val store = result.getArrayElement(1)

        val standardOut = standardOut(result, isFct)

        if (isFct) {
            print(standardOut.joinToString(""))
        } else {
            println("results:")
            println("result-term: $resultTerm")
            if (store.asString() != "()") println("store: $store")
            if (standardOut.isNotEmpty()) println("standard-out: $standardOut")
        }
    }

    /**
     * A bare `.fct` file reports each `print` argument as its own entry, still
     * carrying the quoting and escaping the term was written with; a `.config`
     * file reports them already decoded. Callers that check output must agree
     * with the CLI on which reading applies, so both go through here.
     */
    fun standardOut(result: Value, isFct: Boolean): List<String> =
        (2 until result.arraySize).map { i ->
            val raw = result.getArrayElement(i).asString()
            if (isFct) raw.removeSurrounding("\"").replace("\\n", "\n") else raw
        }

    fun checkExpectations(result: Value): ExpectationOutcome {
        if (!result.hasMembers()) return ExpectationOutcome.NotChecked

        val checked = result.getMember("checked").asBoolean()
        if (!checked) return ExpectationOutcome.NotChecked

        if (result.getMember("passing").asBoolean()) return ExpectationOutcome.Passed

        return ExpectationOutcome.Failed(result.getMember("failureReport").asString())
    }
}

sealed interface ExpectationOutcome {
    data object NotChecked : ExpectationOutcome
    data object Passed : ExpectationOutcome
    data class Failed(val report: String) : ExpectationOutcome
}