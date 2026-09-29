package interpreter

import interpreter.FCTInterpreter.createContext
import interpreter.FCTInterpreter.evalFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * `atomic(X)` "computes X, but controls its potential interleaving with other
 * computations: interleaving is only allowed following a transition of X that
 * emits `yielded(signal)`" (Flowing.cbs).
 *
 * `interleave` may compute its arguments "in any order" and CBS states that
 * "fairness of interleaving is not required, so pure left-to-right computation
 * is allowed". This interpreter takes that allowance, so the two always agree:
 * an `atomic` argument is finished before the next one starts, and `yield`
 * simply has nothing to interleave with. That degenerate scheduler is what the
 * rules below pin.
 */
class TestAtomic {

    private data class Run(val result: String, val standardOut: List<String>)

    private fun run(source: String): Run {
        val file = Files.createTempFile("atomic", ".fct")
        Files.writeString(file, "general { funcon-term: $source; }\ntests { result-term: null-value; }\n")
        val context = createContext()
        return try {
            val result = evalFile(context, file)
            Run(
                result.getArrayElement(0).toString(),
                (2 until result.arraySize).map { result.getArrayElement(it).asString() },
            )
        } finally {
            context.close()
        }
    }

    private fun assertRun(expectedOut: String, source: String) {
        val actual = try {
            run(source)
        } catch (e: Throwable) {
            throw AssertionError("$source unexpectedly failed: ${e::class.simpleName}: ${e.message}", e)
        }
        assertEquals(expectedOut, actual.standardOut.joinToString(", "), "standard-out of `$source`")
    }

    @Test
    fun `atomic of a value is that value`() {
        // Rule `atomic(V:T) ~> V`. This is also why `atomic(tuple())` is not
        // `null-value`; see `sequential requires null-value before its last
        // argument` below.
        assertEquals("tuple()", run("atomic(tuple())").result)
        assertEquals("1", run("atomic(1)").result)
    }

    @Test
    fun `atomic of a computation runs it and yields its value`() {
        assertEquals("null-value", run("atomic print 1").result)
        assertRun("1", "atomic print 1")
    }

    @Test
    fun `yield-on-value emits yielded(signal) and the rest still runs`() {
        // The signal is what would let an enclosing atomic computation be
        // interleaved. With no interleaving to permit it is simply consumed.
        assertRun("7, 8", "atomic sequential(yield-on-value print 7, print 8)")
        assertEquals("1", run("atomic(yield-on-value 1)").result)
    }

    @Test
    fun `interleave runs an atomic argument to completion before the next`() {
        assertRun("4, 5, 6", "interleave(atomic sequential(print 4,print 5), print 6)")
        assertRun("4, 5, 6, 7", "interleave(atomic sequential(print 4,print 5), print 6, print 7)")
    }

    @Test
    fun `yield-on-abrupt lets a failure escape an atomic argument`() {
        assertRun("10, 11", "else(atomic yield-on-abrupt sequential(print 10,fail), print 11)")
    }

    @Test
    fun `an unhandled abrupt leaves the term stuck`() {
        // `abrupt` with no enclosing `else` is stuck, and `atomic` does not
        // swallow it: `yield-on-abrupt` exists precisely to make it visible.
        val error = Assertions.assertThrows(Throwable::class.java) { run("atomic(fail)") }
        Assertions.assertTrue(
            error.message?.contains("StuckException") == true,
            "expected StuckException, got ${error::class.simpleName}: ${error.message}",
        )
    }

    @Test
    fun `the body of atomic config produces its expected output`() {
        // CBS-beta/Funcons-beta/Computations/Normal/Flowing/tests/atomic.config
        // expects `[tuple(), 1, 2, ..., 11]`. Everything except its first
        // argument behaves as expected; that argument is a term whose expected
        // value cannot be produced, so it is left out here. See
        // `sequential requires null-value before its last argument`.
        assertRun(
            "1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11",
            "finalise-failing sequential(" +
                "atomic(print 1)," +
                "atomic sequential(print 2,print 3)," +
                "interleave(atomic sequential(print 4,print 5), print 6)," +
                "interleave(atomic sequential(yield-on-value print 7,print 8), print 9)," +
                "else(atomic yield-on-abrupt sequential(print 10,fail), print 11)" +
                ")",
        )
    }

    @Test
    fun `sequential requires null-value before its last argument`() {
        // `sequential(_:(=>null-type)*, _:=>T)`: the rule that consumes a
        // non-final argument is `sequential(null-value, Y+) ~> sequential(Y+)`,
        // and the rule that steps one needs it to be reducible. A value is
        // neither, so `sequential(atomic(tuple()), Y)` has no applicable rule.
        assertRun("1", "sequential(atomic(null-value), print 1)")
        val error = Assertions.assertThrows(Throwable::class.java) {
            run("sequential(atomic(tuple()), print 1)")
        }
        Assertions.assertTrue(
            error.message?.contains("StuckException") == true,
            "expected StuckException, got ${error::class.simpleName}: ${error.message}",
        )
    }
}
