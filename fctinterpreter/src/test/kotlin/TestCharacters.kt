package interpreter

import interpreter.FCTInterpreter.createContext
import interpreter.FCTInterpreter.evalFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * `unicode-character(_:unicode-points) : unicode-characters` maps a code point
 * to the character with that point. It was an unimplemented abort, and the
 * library's own character constants are all defined through it --
 * `Characters.cbs` gives `line-feed` as `unicode-character(hexadecimal-natural
 * "000a")` -- so `line-feed`, `backspace`, `form-feed`, `double-quote`,
 * `single-quote` and `horizontal-tab` were all stuck.
 *
 * These pin the constants by name, because that is how a term actually refers
 * to a character; spelling out the code point instead would test the
 * conversion but not the definitions that depend on it.
 */
class TestCharacters {

    private fun eval(source: String): String {
        val file = Files.createTempFile("char", ".txt")
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

    /** Standard-out of [source], which is where `print` puts its output. */
    private fun evalOut(source: String): String {
        val file = Files.createTempFile("char", ".txt")
        Files.writeString(file, source)
        val context = createContext()
        return try {
            val result = evalFile(context, file)
            FCTInterpreter.standardOut(result, isFct = false).joinToString("")
        } catch (e: Throwable) {
            "${e::class.simpleName}: ${e.message}"
        } finally {
            context.close()
        }
    }

    @Test
    fun `unicode-character yields the character with that code point`() {
        assertEquals("A", eval("unicode-character(65)"))
        assertEquals("a", eval("unicode-character(97)"))
        assertEquals("0", eval("unicode-character(48)"))
        // The ends of the range Unicode actually defines: 0 to 0x10FFFF.
        assertEquals("true", eval("is-in-type(unicode-character(0), characters)"))
        assertEquals("true", eval("is-in-type(unicode-character(1114111), characters)"))
        // CBS's own bound for `unicode-points` is 0x1FFFFF, but that admits code
        // points denoting no character, and there is nothing to produce there.
        assertTrue(eval("unicode-character(2097151)").contains("unicode-character"))
    }

    @Test
    fun `a character prints as itself`() {
        // `print` returns null-value, so this has to read standard-out, not the
        // result of the term.
        assertEquals("A", evalOut("print(unicode-character(65))"))
    }

    @Test
    fun `the library's character constants are defined and evaluate`() {
        // Each of these is `unicode-character(<base>N"....")` in Characters.cbs.
        assertEquals("\b", eval("backspace"))
        assertEquals("\t", eval("horizontal-tab"))
        assertEquals("\n", eval("line-feed"))
        assertEquals("", eval("form-feed"))
        assertEquals("\"", eval("double-quote"))
        assertEquals("\\", eval("backslash"))
    }

    /**
     * Above the basic multilingual plane a character is a surrogate pair, which
     * does not fit the single `Char` that `CharacterNode` holds, so those come
     * back as the code point's string rather than a `CharacterNode`. The value
     * is still faithful, so it is still a character as far as types go.
     */
    @Test
    fun `an astral code point is still a character`() {
        assertEquals("true", eval("is-in-type(unicode-character(128512), characters)"))
        assertEquals("true", eval("is-in-type(unicode-character(1114111), characters)"))
    }

    /**
     * `unicode-point` is still broken, and this records why rather than hiding
     * it: the rule the generator emits for `unicode-point(unicode-character(UP))
     * ~> UP` matches on the argument still being a `UnicodeCharacterNode`, but
     * `unicode-character` now reduces to the character value, so the round trip
     * has no applicable rule. Fixing it means overriding the generated node,
     * which is a separate change.
     */
    @Test
    fun `the unicode-point round trip is still missing`() {
        assertTrue(eval("unicode-point(unicode-character(65))").contains("unicode-point"))
    }

    private fun assertEquals(expected: String, actual: String) =
        Assertions.assertEquals(expected, actual)

    private fun assertTrue(condition: Boolean) = Assertions.assertTrue(condition)
}
