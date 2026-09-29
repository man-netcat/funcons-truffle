package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*
import java.math.BigInteger

open class CharactersNode : GroundValuesNode(), CharactersInterface

data class CharacterNode(override val value: Char) : CharactersNode() {
    override fun equals(other: Any?): Boolean {
        return other is CharacterNode && this.value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString()
}

class UnicodeCharactersNode : CharactersNode(), UnicodeCharactersInterface

/**
 * `unicode-character(_:unicode-points) : unicode-characters` -- the character
 * with the given code point. `unicode-points` is
 * `bounded-integers(0, unsigned-bit-vector-maximum(21))`, so 0 to 2^21-1, and
 * the funcon is total over that range, so anything outside it is a type error
 * rather than a value to produce.
 *
 * This was an unimplemented abort, which left every character constant the
 * library defines stuck: `line-feed` is `unicode-character(hexadecimal-natural
 * "000a")`, and so are `backspace`, `form-feed`, `double-quote` and
 * `horizontal-tab`.
 *
 * The result is a `CharacterNode`, which is how this interpreter already
 * represents a character -- `charNode` builds them, and a string literal is a
 * sequence of them.
 *
 * Above the basic multilingual plane a character is a surrogate pair, which
 * does not fit the single `Char` that `CharacterNode` holds, so those are
 * returned as the code point's string instead. That keeps the value faithful
 * but means an astral character is not a `CharacterNode`.
 */
class UnicodeCharacterNode(@Eager @Child override var p0: TermNode) : TermNode(), UnicodeCharacterInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        if (!p0.isInIntegers()) abort()
        val point = p0.value as? BigInteger ?: abort()
        if (point < BigInteger.ZERO || point > MAX_POINT) abort()
        val codePoint = point.toInt()
        return if (Character.isBmpCodePoint(codePoint)) {
            ValueNodeFactory.charNode(codePoint.toChar())
        } else {
            ValueNodeFactory.strLiteralNode(Character.toString(codePoint))
        }
    }

    private companion object {
        /**
         * CBS types `unicode-points` as `bounded-integers(0,
         * unsigned-bit-vector-maximum(21))`, whose upper bound is 0x1FFFFF.
         * That is larger than Unicode actually defines, which stops at
         * 0x10FFFF, so the declared range admits code points that denote no
         * character. The real limit is used here, since there is nothing
         * meaningful to produce above it.
         */
        val MAX_POINT: BigInteger = BigInteger.valueOf(Character.MAX_CODE_POINT.toLong())
    }
}

class IsoLatin1CharactersNode : CharactersNode(), IsoLatin1CharactersInterface
class BasicMultilingualPlaneCharactersNode : CharactersNode(), BasicMultilingualPlaneCharactersInterface
class AsciiCharactersNode : CharactersNode(), AsciiCharactersInterface
class UnicodePointsNode : BoundedIntegersNode(IntegerNode(0), UnsignedBitVectorMaximumNode(IntegerNode(21))),
    UnicodePointsInterface