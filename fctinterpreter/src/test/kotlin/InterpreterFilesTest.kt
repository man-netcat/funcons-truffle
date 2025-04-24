package interpreter

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.junit.jupiter.api.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.test.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterpreterFilesTest {

    private lateinit var context: Context

    @BeforeAll
    fun setup() {
        context = Context.newBuilder("fctlang")
            .allowAllAccess(true)
            .build()
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
                runTestFile(context, path, relativePath)
            }
        }
    }

    private fun collectTestFiles(rootDir: Path): List<Path> {
        val blacklist = listOf(
            "Abstraction/Patterns",
            "Abstraction/Thunks",
            "Abstraction/Generic",
            "atomic",
            "structural-assign",
            "catch-else-throw",
        )

        return Files.walk(rootDir)
            .filter { it.isRegularFile() && it.extension == "config" && blacklist.none(it.pathString::contains) }
            .toList()
    }

    private fun runTestFile(context: Context, path: Path, displayName: String) {
        try {
            println(displayName)
            val code = Files.readString(path)
            val source = Source.newBuilder("fctlang", code, displayName).build()
            val result = context.eval(source)
            processResult(result)
        } catch (e: Exception) {
            val root = e.cause ?: e
            val messageLine = "Error in $displayName: ${root::class.qualifiedName}: ${root.message}"
            println(messageLine)
            fail("Exception occurred while testing $displayName")
        } finally {
            println("----------------------------------------")
        }
    }
}