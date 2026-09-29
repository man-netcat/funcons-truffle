package interpreter

import interpreter.FCTInterpreter.createContext
import interpreter.FCTInterpreter.evalFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * `integers` is the type of unbounded integers (Integers.cbs: "integers is the
 * type of unbounded integers"). A `Long`-backed node silently wraps at 2^63,
 * which turns `integer-power(2,64)` into a negative number and makes every
 * equality test on large arithmetic agree with itself for the wrong reason.
 *
 * These pin the values themselves, not the node class, because the reference
 * prints the same terms and is the oracle for the arithmetic.
 */
class TestIntegers {

    private fun eval(source: String): String {
        val file = Files.createTempFile("int", ".txt")
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
    fun `a literal beyond 64 bits is read exactly, not wrapped`() {
        // 2^80. As a Long this is 0, so an unsigned-reading or a narrowing cast
        // would report 0 here.
        assertEquals("1208925819614629174706176", eval("1208925819614629174706176"))
        assertEquals("true", eval("is-equal(1208925819614629174706176,1208925819614629174706176)"))
    }

    @Test
    fun `addition and multiplication past the Long boundary stay exact`() {
        assertEquals("18446744073709551616", eval("integer-add(9223372036854775807,9223372036854775809)"))
        assertEquals("1208925819614629174706176", eval("integer-multiply(1099511627776,1099511627776)"))
    }

    @Test
    fun `integer-power is exact rather than saturating`() {
        // The reference prints 18446744073709551616 for 2^64.
        assertEquals("18446744073709551616", eval("integer-power(2,64)"))
        assertEquals("1267650600228229401496703205376", eval("integer-power(2,100)"))
        assertEquals("1", eval("integer-power(2,0)"))
    }

    @Test
    fun `negative arithmetic stays exact`() {
        assertEquals("-18446744073709551616", eval("integer-negate(18446744073709551616)"))
        assertEquals("0", eval("integer-add(18446744073709551616,integer-negate(18446744073709551616))"))
    }

    @Test
    fun `equality does not see a large integer as a wrapped one`() {
        // 2^64 truncates to 0 in a Long, so a narrowing read makes the largest
        // representable power of two equal to zero.
        assertEquals("false", eval("is-equal(18446744073709551616,0)"))
        assertEquals("true", eval("is-equal(18446744073709551616,integer-power(2,64))"))
    }

    @Test
    fun `division and modulo of large values stay exact`() {
        // 2^64 is even, so the remainder is 0 and the quotient is 2^63; a
        // narrowing read would report 0 for both.
        assertEquals("0", eval("integer-modulo(18446744073709551616,2)"))
        assertEquals("9223372036854775808", eval("integer-divide(18446744073709551616,2)"))
    }

    @Test
    fun `small integers still work`() {
        assertEquals("3", eval("integer-add(1,2)"))
        assertEquals("2", eval("integer-divide(7,3)"))
        assertEquals("1", eval("integer-modulo(7,3)"))
        assertEquals("3", eval("natural-successor(2)"))
    }

    private fun assertEquals(expected: String, actual: String) =
        Assertions.assertEquals(expected, actual)
}
