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
    fun stringLiteralRoundTripTest() {
        // If the parser kept the quotes in the value, this printed `""1""` and
        // the comparison below failed.
        assertEquals("\"1\"", eval("to-string(1)"))
        assertEquals("true", eval("is-equal(to-string(1),\"1\")"))
        assertEquals("true", eval("is-equal(to-string(true),\"true\")"))
    }

    @Test
    fun stringLiteralPrintingTest() {
        // A literal keeps its delimiters when it prints, and doubles none of them.
        assertEquals("\"ab\"", eval("\"ab\""))
        assertEquals("true", eval("is-equal(\"ab\",\"ab\")"))
        assertEquals("false", eval("is-equal(\"ab\",\"cd\")"))
    }

    @Test
    fun toStringTest() {
        // Assert to-string(S:strings) == S in Strings.cbs.
        assertEquals("true", eval("is-equal(to-string(\"ab\"),\"ab\")"))
    }

    @Test
    fun datatypeValuePrintingTest() {
        // A datatype value carries an identifier, and the same `StringLiteral`
        // class is reused for string literals. If the identifier is printed
        // through the string printer, `null-value` comes out as `"null-value"`,
        // which also breaks class lookup by name. The reference prints it bare.
        assertEquals("null-value", eval("null-value"))
        assertEquals("tuple(1, 2)", eval("tuple(1,2)"))
    }

    @Test
    fun datatypeValueIdTest() {
        // The counterpart of the above: the *identifier* is a string, so the
        // reference quotes it. Guards against "fixing" the test above too far.
        assertEquals("\"null-value\"", eval("datatype-value-id(null-value)"))
    }

    @Test
    fun stringAsListTest() {
        // `isIn<lists>` used to recognise only `ValueListNode`, so a string was
        // not a list and every `lists` funcon was stuck on one. This is the
        // single-argument `list-append(L:lists(_)) ~> L` rule.
        assertEquals("\"a\"", eval("list-append(\"a\")"))
    }

    private fun assertEquals(expected: String, actual: String) =
        Assertions.assertEquals(expected, actual)
}
