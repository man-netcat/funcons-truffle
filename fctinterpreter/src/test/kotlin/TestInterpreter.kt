package interpreter

import interpreter.FCTInterpreter.checkExpectations
import interpreter.FCTInterpreter.createContext
import interpreter.FCTInterpreter.evalFile
import interpreter.FCTInterpreter.processResult
import interpreter.ExpectationOutcome
import language.StuckException
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.opentest4j.TestAbortedException
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseTest(
    private val testDirectories: List<Pair<Path, String>>,
    private val performTiming: Boolean = true,
    private val iterations: Int = 50,
    private val warmUpIterations: Int = 10,
    private val excludedFiles: List<String> = emptyList(),
    private val knownCrashes: Map<String, String> = emptyMap(),
    private val knownDivergences: Map<String, String> = emptyMap()
) {

    private val originalOut: PrintStream? = System.out
    private val dummyOut = PrintStream(object : OutputStream() {
        override fun write(b: Int) {}
    })

    @TestFactory
    fun generateTests(): List<DynamicTest> =
        testDirectories.flatMap { (dir, ext) ->
            collectTestFiles(dir, ext).map { path ->
                val displayName = relativePath(dir, path)
                dynamicTest(displayName) {
                    printHeader(displayName)
                    val outcome = runGuarded(displayName, path)
                    when (outcome) {
                        is ExpectationOutcome.Failed -> throw AssertionError(
                            "declared expectations not met:\n${outcome.report}"
                        )
                        else -> Unit
                    }
                }
            }
        }

    private fun runGuarded(displayName: String, path: Path): ExpectationOutcome {
        val context = createContext()
        try {
            val result = evalFile(context, path)
            processResult(result, path)
            val outcome = checkExpectations(result)
            knownDivergences[displayName]?.let { reason ->
                val detail = when (outcome) {
                    is ExpectationOutcome.Failed -> "\n${outcome.report}"
                    else -> "\n(matched on this run; output is not deterministic, so not asserted)"
                }
                throw TestAbortedException("known v1 divergence [$reason]:$detail")
            }
            return outcome
        } catch (e: Exception) {
            val reason = knownCrashes[displayName]
            if (reason != null && isStuck(e)) {
                throw TestAbortedException("known v1 semantic gap: $reason", e)
            }
            throw e
        } finally {
            context.close()
        }
    }

    private fun isStuck(error: Throwable): Boolean {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            if (current is StuckException) return true
            if (current.message?.contains("StuckException") == true) return true
            current = current.cause
        }
        return false
    }

    private fun runWithTiming(path: Path) {
        val times = mutableListOf<Long>()

        repeat(iterations) { iteration ->
            val context = createContext()
            if (iteration == 0) {
                System.setOut(originalOut)
            } else {
                System.setOut(dummyOut)
            }

            try {
                val start = System.nanoTime()
                val result = evalFile(context, path)
                processResult(result, path)
                val end = System.nanoTime()
                if (iteration >= warmUpIterations) {
                    times.add(end - start)
                }
            } finally {
                context.close()
                System.setOut(originalOut)
            }
        }

        check(times.isNotEmpty()) {
            "no samples recorded: iterations=$iterations warmUpIterations=$warmUpIterations"
        }

        times.sort()
        val min = times.first()
        val max = times.last()
        val median = times[times.size / 2]
        val average = times.average()
        val stddev = kotlin.math.sqrt(times.map { (it - average).let { d -> d * d } }.average())
        val p90 = times[(times.size * 0.9).toInt()]

        fun formatNs(ns: Double) = "${(ns / 1_000_000).format(3)} ms"

        println()
        println("Min:             ${formatNs(min.toDouble())}")
        println("Median:          ${formatNs(median.toDouble())}")
        println("Average:         ${formatNs(average)}")
        println("90th percentile: ${formatNs(p90.toDouble())}")
        println("Max:             ${formatNs(max.toDouble())}")
        println("Std deviation:   ${formatNs(stddev)}")
    }

    protected fun collectTestFiles(rootDir: Path, extension: String): List<Path> {
        val absoluteRoot = rootDir.normalize().toAbsolutePath()
        return Files.walk(absoluteRoot)
            .filter { it.isRegularFile() && it.extension == extension }
            .filter {
                !excludedFiles.contains(it.fileName.toString())
            }
            .sorted()
            .toList()
    }

    protected fun relativePath(rootDir: Path, file: Path): String {
        val absoluteRoot = rootDir.normalize().toAbsolutePath()
        return absoluteRoot.relativize(file).pathString
    }

    protected fun printHeader(displayName: String) {
        val line = "-".repeat(displayName.length)
        println(line)
        println(displayName)
        println(line)
    }

    protected fun Double.format(digits: Int) = "%.${digits}f".format(this)
}

class TestFunconsBeta : BaseTest(
    testDirectories = listOf(Paths.get("../CBS-beta/Funcons-beta") to "config"),
    performTiming = false,
    knownCrashes = KNOWN_V1_CRASHES,
    knownDivergences = KNOWN_V1_DIVERGENCES
)

private val KNOWN_V1_CRASHES = mapOf(
    "Computations/Normal/Flowing/tests/atomic.config" to
        "StuckException: sequential - the reference reports `unknown funcon: atomic` " +
        "and fails the same interleave sort checks, so there is no oracle for this config"
)

private val KNOWN_V1_DIVERGENCES = emptyMap<String, String>()
