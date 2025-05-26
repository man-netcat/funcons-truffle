package interpreter

import org.graalvm.polyglot.Context
import org.junit.jupiter.api.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterpreterFilesTest {

    private lateinit var context: Context

    @BeforeAll
    fun setup() {
        context = FCTInterpreter.createContext()
    }

    @AfterAll
    fun tearDown() {
        context.close()
    }

    @TestFactory
    fun testFiles(): List<DynamicTest> {
        val configTests = collectAndCreateTests(
            rootDir = Paths.get("../CBS-beta/Funcons-beta"),
            extension = "config"
        )

        val fctTests = collectAndCreateTests(
            rootDir = Paths.get("../fctfiles"),
            extension = "fct"
        )

        return configTests + fctTests
    }

    private fun collectAndCreateTests(rootDir: Path, extension: String): List<DynamicTest> {
        val absoluteRoot = rootDir.normalize().toAbsolutePath()
        return Files.walk(absoluteRoot)
            .filter { it.isRegularFile() && it.extension == extension }
            .sorted()
            .map { path ->
                val relativePath = absoluteRoot.relativize(path).pathString
                DynamicTest.dynamicTest("Testing $relativePath") {
                    runTestFile(path, relativePath)
                }
            }
            .toList()
    }

    private fun runTestFile(path: Path, displayName: String) {
        println(displayName)
        val result = FCTInterpreter.evalFile(context, path)
        FCTInterpreter.processResult(result)
        println("----------------------------------------")
    }
}
