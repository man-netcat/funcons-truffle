package language

import builtin.CharacterNode
import builtin.SequenceNode

object Util {
    //    const val DEBUG = true
    const val DEBUG = false
}

fun toCharSequence(str: String): SequenceNode {
    return SequenceNode(*str.map { char -> CharacterNode(char) }.toTypedArray())
}