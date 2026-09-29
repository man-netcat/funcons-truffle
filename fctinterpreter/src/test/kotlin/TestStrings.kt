package interpreter

import interpreter.FCTInterpreter.createContext
import interpreter.FCTInterpreter.evalFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * A parsed `STRING` token still carries its `"` delimiters, while `ToStringNode`
 * built its result from the unquoted content. The two therefore disagreed, and
 * the mismatch leaked into anything that round-trips a string through
 * `to-string` or reuses a string as a datatype identifier.
 *
 * The reference is the oracle: it prints `to-string(1)` as `"1"` and accepts
 * `is-equal(to-string(1),"1")`.
 */
class TestStrings {

    private fun eval(source: String): String {
        val file = Files.createTempFile("str", ".txt")
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
    fun `a parsed string literal round-trips through to-string`() {
        // If the parser kept the quotes in the value, this printed `""1""` and
        // the comparison below failed.
        assertEquals("\"1\"", eval("to-string(1)"))
        assertEquals("true", eval("is-equal(to-string(1),\"1\")"))
        assertEquals("true", eval("is-equal(to-string(true),\"true\")"))
    }

    @Test
    fun `a string literal prints with its delimiters and no doubled ones`() {
        assertEquals("\"ab\"", eval("\"ab\""))
        assertEquals("true", eval("is-equal(\"ab\",\"ab\")"))
        assertEquals("false", eval("is-equal(\"ab\",\"cd\")"))
    }

    @Test
    fun `to-string of a string returns it unchanged`() {
        // Assert to-string(S:strings) == S in Strings.cbs.
        assertEquals("true", eval("is-equal(to-string(\"ab\"),\"ab\")"))
    }

    @Test
    fun `a datatype value prints its identifier unquoted`() {
        // A datatype value carries an identifier, and the same `StringLiteral`
        // class is reused for string literals. If the identifier is printed
        // through the string printer, `null-value` comes out as `"null-value"`,
        // which also breaks class lookup by name. The reference prints it bare.
        assertEquals("null-value", eval("null-value"))
        assertEquals("tuple(1, 2)", eval("tuple(1,2)"))
    }

    @Test
    fun `datatype-value-id yields a string, so it is printed quoted`() {
        // The counterpart of the above: the *identifier* is a string, so the
        // reference quotes it. Guards against "fixing" the test above too far.
        assertEquals("\"null-value\"", eval("datatype-value-id(null-value)"))
    }

    private fun assertEquals(expected: String, actual: String) =
        Assertions.assertEquals(expected, actual)
}
