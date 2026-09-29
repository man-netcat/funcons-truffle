package interpreter

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.pathString

/**
 * Times the evaluator over the Funcons-beta config corpus, and reports whether a
 * parsed program can be run twice.
 *
 * Two timed modes, because the question is which of two costs dominates:
 *
 *  - **cold**: a fresh polyglot `Context` per program. This is what the CLI and
 *    every test do (`FCTInterpreter.main`, `BaseTest.runGuarded`), so it pays for
 *    engine setup, parsing, and one reduction.
 *  - **shared**: one `Context` for the whole corpus, so the engine and the language
 *    instance are paid for once. Each repetition is filed under a distinct source
 *    name, which forces a genuine re-parse; see [reExecutionIsSound] for why that
 *    qualification is necessary rather than pedantic.
 *
 * The gap between the two is the part of the cost that reusing an engine would
 * remove, which is the premise Truffle is built on. Closing it means the runtime
 * has to stop paying for a fresh `Context` per program.
 *
 * Reports the best of `repeats` runs per program: a min is the stable statistic
 * here, since every run does identical work.
 *
 * Run with `./gradlew :fctinterpreter:benchmark --args="[corpusDir] [repeats]"`.
 */
object Benchmark {
    private const val DEFAULT_CORPUS = "../CBS-beta/Funcons-beta"
    private const val DEFAULT_REPEATS = 5
    private const val SLOWEST = 10

    @JvmStatic
    fun main(args: Array<String>) {
        val corpus = Paths.get(args.getOrElse(0) { DEFAULT_CORPUS }).toAbsolutePath().normalize()
        val repeats = args.getOrNull(1)?.toInt() ?: DEFAULT_REPEATS
        val programs = configFiles(corpus)

        println("corpus:   $corpus")
        println("programs: ${programs.size}   repeats: $repeats")

        if (programs.isEmpty()) {
            println("no .config files found; is the corpus checked out?")
            return
        }

        val cold = timed(programs, repeats) { program, _ ->
            FCTInterpreter.createContext().use { context -> attempt(context) { FCTInterpreter.evalFile(context, program) } }
        }
        val shared = FCTInterpreter.createContext().use { context ->
            timed(programs, repeats) { program, repetition ->
                attempt(context) { evalAs(context, program, "${program.fileName}#$repetition") }
            }
        }

        report("cold: a fresh Context per program", cold)
        report("shared: one Context, re-parsed per program", shared)
        reportGap(cold, shared)
        reportReExecution(cold, programs)
    }

    /**
     * Times `eval`, keeping the fastest of `repeats` attempts and the last result.
     */
    private fun timed(
        programs: List<Path>,
        repeats: Int,
        eval: (Path, Int) -> String
    ): Map<Path, Measurement> = programs.associateWith { program ->
        var best = Long.MAX_VALUE
        var result = ""
        repeat(repeats) { repetition ->
            val started = System.nanoTime()
            result = eval(program, repetition)
            best = minOf(best, System.nanoTime() - started)
        }
        Measurement(best, result)
    }

    private fun configFiles(root: Path): List<Path> =
        root.toFile().walkTopDown()
            .filter { it.isFile && it.extension == "config" }
            .map { it.toPath() }
            .sorted()
            .toList()

    /**
     * Some programs in the corpus get stuck, which the test suite records as known
     * divergences. Those are timed like any other program; the failure is folded into
     * the result so the run keeps going and both modes still agree about what
     * happened.
     */
    private fun attempt(context: Context, eval: () -> Value): String =
        try {
            describe(eval())
        } catch (failure: Exception) {
            "failed: ${failure::class.simpleName}: ${failure.message?.lineSequence()?.first()}"
        }

    private fun evalAs(context: Context, program: Path, name: String): Value {
        val source = Source.newBuilder("fctlang", Files.readString(program), name).build()
        return context.eval(source)
    }

    /**
     * The whole result, as the CLI reads it: the result term, the store, and every
     * `standard-out` entry.
     */
    private fun describe(value: Value): String =
        (0 until value.arraySize).joinToString("|") { index ->
            value.getArrayElement(index).asString()
        }

    /**
     * Runs each program twice under one source name in a single `Context` and
     * compares the second run against the cold result.
     *
     * This is a correctness check, not a timing. Evaluating the same source twice in
     * one `Context` does not re-parse: polyglot caches the `CallTarget` by source
     * name and content, so the second evaluation re-runs the AST built by the first.
     * That is only sound if reduction leaves the AST alone, and it does not:
     * `SequenceNode.popFirst` and `AtomicNode`'s `s0`/`s1` reassign `@Children`, so
     * the second evaluation reduces a term the first one already consumed. Programs
     * reported here are the ones whose cached AST is not reusable, which is the
     * precondition for amortizing anything across evaluations.
     */
    private fun reportReExecution(cold: Map<Path, Measurement>, programs: List<Path>) {
        val reused = FCTInterpreter.createContext().use { context ->
            programs.associateWith { program ->
                val name = program.pathString
                attempt(context) { FCTInterpreter.evalFile(context, program) }
                attempt(context) { FCTInterpreter.evalFile(context, program) }
            }
        }
        val diverged = cold.keys.filter { cold.getValue(it).result != reused.getValue(it) }

        println()
        if (diverged.isEmpty()) {
            println("re-running a parsed program reproduces it: the AST survives a second run")
        } else {
            println("${diverged.size}/${programs.size} programs do not survive a second run " +
                "in the same Context; their AST is mutated during reduction:")
            diverged.take(SLOWEST).forEach { println("  ${it.fileName}") }
        }
    }

    private fun report(label: String, measurements: Map<Path, Measurement>) {
        val times = measurements.values.map { it.nanos }.sorted()
        println()
        println(label)
        println("  total:  ${formatNanos(times.sum().toDouble())}")
        println("  median: ${formatNanos(times[times.size / 2].toDouble())}")
        println("  p90:    ${formatNanos(times[times.size * 9 / 10].toDouble())}")
        println("  slowest:")
        measurements.entries.sortedByDescending { it.value.nanos }.take(SLOWEST).forEach { (program, run) ->
            println("    ${formatNanos(run.nanos.toDouble()).padStart(9)}  ${program.fileName}")
        }
    }

    private fun reportGap(cold: Map<Path, Measurement>, shared: Map<Path, Measurement>) {
        val coldTotal = cold.values.sumOf { it.nanos }.toDouble()
        val sharedTotal = shared.values.sumOf { it.nanos }.toDouble()
        val saved = if (coldTotal == 0.0) 0.0 else 100.0 * (coldTotal - sharedTotal) / coldTotal
        println()
        println("reusing one Context removes %.1f%% of the total".format(saved))
    }

    private fun formatNanos(nanos: Double): String =
        if (nanos >= 1_000_000_000) "%.2fs".format(nanos / 1_000_000_000)
        else if (nanos >= 1_000_000) "%.1fms".format(nanos / 1_000_000)
        else "%.0fus".format(nanos / 1_000)

    private data class Measurement(val nanos: Long, val result: String)
}
