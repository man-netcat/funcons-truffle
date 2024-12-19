package interpreter

import com.oracle.truffle.api.source.Source
import java.nio.file.Files
import java.nio.file.Paths

class FCTInterpreter(private val language: FCTLanguage) {
    fun interpret(code: String): Any? {
        // Convert code into a Source object
        val source = Source.newBuilder(FCTLanguage.ID, code, "<stdin>").build()

        // Parse the source to obtain a CallTarget
        val callTarget = language.parseSource(source)

        // Execute the CallTarget and return the result
        return callTarget.call()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: FCTInterpreter <file_path>")
                return
            }

            // Read the file from the command line argument
            val filePath = args[0]
            val code = try {
                Files.readString(Paths.get(filePath))
            } catch (e: Exception) {
                println("Error reading file: ${e.message}")
                return
            }

            // Create an instance of FCTLanguage (ensure it's properly instantiated in your environment)
            val language = FCTLanguage()

            // Create the interpreter and interpret the code
            val interpreter = FCTInterpreter(language)
            val result = interpreter.interpret(code)

            println("Execution Result: $result")
        }
    }
}
