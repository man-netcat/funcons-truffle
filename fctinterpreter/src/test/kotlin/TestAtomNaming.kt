package interpreter

import interpreter.FCTInterpreter.createContext
import interpreter.FCTInterpreter.evalFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * An atom's name is its content: `atom("@1")` is the atom named `@1`. The
 * reference agrees, printing `atom(@1)` where the corpus writes `atom("@1")`.
 *
 * Two places have to agree on that convention or variable identity silently
 * breaks: the `atom(...)` built-in, and the fresh atoms minted by
 * `element-not-in(atoms, _)`. They are pinned together here because a mismatch
 * makes `check-true is-equal(current-value(given), ...)` fail deep inside
 * Computations/Normal/Storing.
 */
class TestAtomNaming {

    private fun eval(source: String): String {
        val file = Files.createTempFile("atom", ".txt")
        Files.writeString(file, source)
        val context = createContext()
        return try {
            val result = evalFile(context, file)
            result.getArrayElement(0).let { if (it.isString) it.asString() else it.toString() }
        } catch (e: Throwable) {
            "${e::class.simpleName}: ${e.message}"
        } finally {
            context.close()
        }
    }

    @Test
    fun `atom name is the string content, not its quoted token`() {
        assertEquals("atom(ab)", eval("atom(\"ab\")"))
        assertEquals("atom(@1)", eval("atom(\"@1\")"))
    }

    @Test
    fun `an atom with a space in its name keeps the space`() {
        assertEquals("atom(a b)", eval("atom(\"a b\")"))
    }

    @Test
    fun `fresh atoms are named the same way as atom built by name`() {
        // `allocate-initialised-variable` derives its variable name from
        // `element-not-in(atoms, _)`; a config then refers to it as
        // `atom("@1")`. If the two disagree the variable is unnameable.
        val generated = eval("element-not-in(atoms, set())")
        assertEquals("atom(@1)", generated)
        assertEquals(eval("atom(\"@1\")"), generated)
    }

    @Test
    fun `two atoms with the same content are equal, different content is not`() {
        assertEquals("true", eval("is-equal(atom(\"ab\"),atom(\"ab\"))"))
        assertEquals("false", eval("is-equal(atom(\"ab\"),atom(\"cd\"))"))
    }

    private fun assertEquals(expected: String, actual: String) =
        Assertions.assertEquals(expected, actual)
}
