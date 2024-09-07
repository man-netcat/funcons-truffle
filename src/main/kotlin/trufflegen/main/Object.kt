package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext

abstract class Object(private val aliases: List<AliasDefinitionContext>) {
    abstract val name: String
    abstract fun generateCode(): String

    fun aliasStr(): String {
        return aliases.joinToString("\n") { alias -> makeTypeAlias(toClassName(alias.name.text), nodeName) }
    }

    internal val nodeName: String
        get() {
            return toClassName(name)
        }
}
