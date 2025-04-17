package builtin

import com.oracle.truffle.api.frame.VirtualFrame
import generated.*

open class CharactersNode : GroundValuesNode(), CharactersInterface

data class CharacterNode(override val value: Char) : CharactersNode() {

    override fun equals(other: Any?): Boolean = when (other) {
        is CharacterNode -> this.value == other.value
        else -> false
    }

    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.toString()
}

class UnicodeCharactersNode : CharactersNode(), UnicodeCharactersInterface

class UnicodeCharacterNode(override val p0: TermNode) : TermNode(), UnicodeCharacterInterface {
    override fun reduceRules(frame: VirtualFrame): TermNode {
        TODO("Not yet implemented: $name")
    }
}

class IsoLatin1CharactersNode : CharactersNode(), IsoLatin1CharactersInterface
class BasicMultilingualPlaneCharactersNode : CharactersNode(), BasicMultilingualPlaneCharactersInterface
class AsciiCharactersNode : CharactersNode(), AsciiCharactersInterface
