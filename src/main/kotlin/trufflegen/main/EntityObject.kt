package trufflegen.main

import trufflegen.antlr.CBSParser.AliasDefinitionContext

class EntityObject(override val name: String, aliases: List<AliasDefinitionContext>) : Object(aliases) {
    override fun generateCode(): String {
        TODO("Not yet implemented")
    }
}