package interpreter

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.*
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.test.assertTrue
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
        val blackListedFuncons = listOf<String>(
//            "Abstraction/Patterns",
//            "Abstraction/Thunks",
//            "Abstraction/Generic",
//            "Composite/Classes",
//            "atomic",
//            "structural-assign",
//            "catch-else-throw",
//            "handle-recursively",
//            "fold-right",
        )

        val rootDir = Paths.get("../CBS-beta/Funcons-beta").normalize().toAbsolutePath()
        val testFiles = Files.walk(rootDir)
            .filter { path ->
                val conditions = listOf(
                    path.isRegularFile(),
                    path.extension == "config",
                    !blackListedFuncons.any { subPath -> subPath in path.pathString }
                )
                conditions.all { it }
            }
            .toList()

        return testFiles.sorted().map { path ->
            val relativePath = rootDir.relativize(path).pathString

            DynamicTest.dynamicTest("Testing $relativePath") {
                try {
                    println(relativePath)
                    val code = Files.readString(path)
                    val source = Source.newBuilder("fctlang", code, relativePath).build()
                    val result: Value = context.eval(source)

                    assertTrue(result.hasArrayElements(), "Result from $relativePath is not an array")

                    val resultTerm = result.getArrayElement(0)
                    val standardOut =
                        "[" + (1 until result.arraySize).map { result.getArrayElement(it) }.joinToString(",") + "]"

                    println("Result-term: $resultTerm")
                    println("Standard-out: $standardOut")

                    assertTrue(!resultTerm.isNull, "Result term was null in $relativePath")
                } catch (e: Exception) {
                    val root = e.cause ?: e
                    val messageLine = "Error in $relativePath: ${root::class.qualifiedName}: ${root.message}"

                    println(messageLine)

                    fail("Exception occurred while testing $relativePath")
                } finally {
                    println("----------------------------------------")
                }
            }
        }
    }
}
