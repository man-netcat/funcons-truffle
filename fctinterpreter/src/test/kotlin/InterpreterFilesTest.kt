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
        val rootDir = Paths.get("../CBS-beta/Funcons-beta").normalize().toAbsolutePath()
        val testFiles = collectTestFiles(rootDir)

        return testFiles.sorted().map { path ->
            val relativePath = rootDir.relativize(path).pathString
            DynamicTest.dynamicTest("Testing $relativePath") {
                runTestFile(path, relativePath)
            }
        }
    }

    private fun collectTestFiles(rootDir: Path): List<Path> {
        val blacklist = listOf<String>(
//            "pattern-bind.config",
        )

        return Files.walk(rootDir)
//            .filter { it.isRegularFile() && it.extension == "config" && blacklist.any(it.pathString::contains) }
            .filter { it.isRegularFile() && it.extension == "config" && blacklist.none(it.pathString::contains) }
            .toList()
    }

    private fun runTestFile(path: Path, displayName: String) {
        println(displayName)
        val result = FCTInterpreter.evalFile(context, path)
        FCTInterpreter.processResult(result)
        println("----------------------------------------")
    }
}
