package interpreter

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The IMP (Imperative MiniLanguage) programs from
 * `CBS/Languages/IMP/IMP-Tests/Coverage`, ported from the 2016 CBS to
 * Funcons-beta. All nine are whole programs with known final variable values,
 * which makes them a check on composition rather than on any one funcon.
 *
 * Porting is not a rename. The 2016 term for `int x; x = 3;` is
 *
 * ```
 * scope (map-unite (bind ("x", allocate-initialised-variable (integers, 0))),
 *        assign (bound ("x"), decimal-natural ("3")))
 * ```
 *
 * and Funcons-beta has no `scope`-that-binds: there `scope` pushes an
 * environment, and a mutable variable is introduced by
 * `initialise-storing` and `give`, with `given` standing for the location and
 * `assigned(given)` for its value. That is the shape
 * `Computations/Normal/Storing/tests/allocate-initialised-variable.config` uses,
 * so the port follows it.
 *
 * The other change is that multiple variables are held in a vector of locations
 * and reached by `index` rather than by name, because a nested `give` rebinds
 * the name `given` and would shadow the outer variable.
 *
 * Renames applied: `while` is `while-true`, `if-then-else` is `if-true-else`,
 * `is-less-or-equal` is `integer-is-less-or-equal`, and `&&` is `and`.
 *
 * The 2016 suite asserts on a rendering of the final store, e.g.
 * `simple-variable(@0,integers) |-> 3`, which is a property of the reference's
 * pretty-printer rather than of the language. These instead assert the final
 * value of each variable, which is the same information.
 */
class TestImpPrograms {

    private val programsDir: Path = Paths.get("src/test/resources/imp-programs")

    @TestFactory
    fun impPrograms(): List<DynamicTest> {
        if (!Files.isDirectory(programsDir)) {
            error("IMP program directory not found: $programsDir")
        }
        val programs = Files.list(programsDir).use { stream ->
            stream.filter { it.toString().endsWith(".config") }
                .sorted { a, b -> a.fileName.toString().compareTo(b.fileName.toString()) }
                .toList()
        }
        check(programs.size == 9) { "expected the 9 IMP Coverage programs, found ${programs.size}" }
        return programs.map { program -> DynamicTest.dynamicTest(program.fileName.toString()) { check(program) } }
    }

    private fun check(program: Path) {
        val context = FCTInterpreter.createContext()
        val outcome = try {
            FCTInterpreter.checkExpectations(FCTInterpreter.evalFile(context, program))
        } finally {
            context.close()
        }
        when (outcome) {
            is ExpectationOutcome.Failed ->
                throw AssertionError("${program.fileName}: ${outcome.report}")
            ExpectationOutcome.NotChecked ->
                throw AssertionError("${program.fileName}: no `tests` block, so nothing was checked")
            ExpectationOutcome.Passed -> Unit
        }
    }
}
