package interpreter

import org.junit.jupiter.api.Test

class TestInterpreter {

    @Test
    fun testInterpreterWithFileNames() {
        val fileNames = listOf(
            "src/test/resources/testScript1.fct",
            "src/test/resources/testScript2.fct",
            "src/test/resources/testScript3.fct"
        )

        for (fileName in fileNames) {
            println("Executing file: $fileName")
            // Execute the interpreter's main function with the filename as argument.
            main(arrayOf(fileName))
        }
    }
}
