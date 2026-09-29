package interpreter

import org.graalvm.polyglot.Value
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Whole-program regression tests over the MiniJava benchmark suite.
 *
 * The `.fct` files under `fctfiles/minijava/` are Funcons translations of the
 * rascal-minijava examples, each shipped with the program's expected stdout in
 * a matching `.output` file. They are the largest terms we run -- up to 34 kB,
 * tens of `sequential`, `give` and `while-true` each -- and they were
 * previously referenced by no test at all, so nothing caught a regression in
 * them.
 *
 * Each program is asserted line by line against its `.output` file.
 */
class TestMinijava {

    // Gradle runs tests with the module directory as the working directory,
    // which is why the corpus test reaches the vendored tree via `../`.
    private val programsDir: Path = Paths.get("../fctfiles/minijava")

    /**
     * Programs that currently produce only a *prefix* of their expected output
     * and then stop, while still reporting `result-term: null-value` and
     * exiting cleanly. No exception, no stuck term, just missing output.
     *
     * Each entry is the number of leading expected lines we do produce, and it
     * is asserted exactly: the output must be precisely those lines and
     * nothing more, and must match them byte for byte. So this does not skip
     * the failure, it pins it. Any change in behaviour -- including a fix --
     * turns this test red, which is the point.
     *
     * This used to hold all eight programs, every one of which used
     * `while-true`, which made loop control the suspect. Removing the
     * `Node.replace` and `Node.insert` calls from the reduction path is what
     * fixed them: a step variable is reduced in place, so the reduction has to
     * write the reduced subterm back into the term it is walking, and those two
     * calls were doing that by surgery on the adopted AST, which a cached AST
     * does not survive.
     */
    private val stopsAfterLines = mapOf<String, Int>()

    @TestFactory
    fun minijavaPrograms(): List<DynamicTest> {
        if (!Files.isDirectory(programsDir)) {
            error("MiniJava program directory not found: $programsDir")
        }
        val programs = Files.list(programsDir).use { stream ->
            stream.filter { it.extension == "fct" }
                .sorted { a, b -> a.name.compareTo(b.name) }
                .toList()
        }
        check(programs.isNotEmpty()) { "no MiniJava programs found in $programsDir" }
        return programs.map { program -> DynamicTest.dynamicTest(program.name) { check(program) } }
    }

    private fun check(program: Path) {
        val name = program.name.removeSuffix(".fct")
        val expectedFile = program.resolveSibling("$name.output")
        check(Files.exists(expectedFile)) { "no expected-output file for $name" }

        val expectedLines = Files.readString(expectedFile)
            .replace("\r\n", "\n")
            .trimEnd('\n')
            .split('\n')

        val producedLines = producedLines(program)
        val stopsAfter = stopsAfterLines[name]
        val wanted = if (stopsAfter == null) expectedLines.size else stopsAfter

        // The assertion below is the whole test. For a working program `wanted`
        // is the entire expected output, so this demands a byte-exact match.
        // For a truncating program it demands exactly the recorded prefix --
        // correct lines, and no more -- so a partial fix cannot pass unnoticed.
        assertEquals(
            expectedLines.take(wanted).joinToString("\n"),
            producedLines.joinToString("\n"),
            if (stopsAfter == null) {
                "$name should reproduce all ${expectedLines.size} expected output lines"
            } else {
                "$name is recorded as stopping after $wanted of ${expectedLines.size} lines; " +
                    "if that is now fixed, remove it from stopsAfterLines instead of widening it"
            },
        )
    }

    private fun producedLines(program: Path): List<String> {
        val context = FCTInterpreter.createContext()
        try {
            val result: Value = FCTInterpreter.evalFile(context, program)
            val out = FCTInterpreter.standardOut(result, isFct = true).joinToString("")
            return out.trimEnd('\n').split('\n').let { if (it.size == 1 && it[0].isEmpty()) emptyList() else it }
        } finally {
            context.close()
        }
    }

    private fun assertEquals(expected: String, actual: String, message: String) {
        if (expected != actual) {
            throw AssertionError(
                "$message\n--- expected ---\n$expected\n--- actual ---\n$actual\n--------------"
            )
        }
    }
}
