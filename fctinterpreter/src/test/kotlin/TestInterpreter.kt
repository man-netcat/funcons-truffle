package interpreter

import interpreter.FCTInterpreter.createContext
import interpreter.FCTInterpreter.evalFile
import interpreter.FCTInterpreter.processResult
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
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
                    if (performTiming) {
                        runWithTiming(path)
                    } else {
                        runOnce(path)
                    }
                }
            }
        }

    private fun runOnce(path: Path) {
        val context = createContext()
        try {
            val result = evalFile(context, path)
            processResult(result, path)
        } finally {
            context.close()
        }
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
)

class TestStandaloneFCT : BaseTest(
    testDirectories = listOf(Paths.get("../fctfiles/tests") to "fct"),
)

class TestMiniJava : BaseTest(
    testDirectories = listOf(Paths.get("../fctfiles/minijava") to "fct"),
    performTiming = false
)

