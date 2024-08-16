package trufflegen.main

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.source.Source
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import trufflegen.antlr4.FCTLexer
import trufflegen.antlr4.FCTParser

@TruffleLanguage.Registration(
    id = CBSLanguage.ID,
    name = "cbslang",
    defaultMimeType = CBSLanguage.MIME_TYPE,
    characterMimeTypes = [CBSLanguage.MIME_TYPE]
)
class CBSLanguage : TruffleLanguage<Nothing>() {
    companion object {
        const val ID: String = "cbslang"
        const val MIME_TYPE: String = "application/x-cbslang"
    }

    override fun createContext(env: Env?): Nothing {
        TODO("Not yet implemented")
    }

    fun parse(source: Source, vararg argumentNames: String): CallTarget {
        val code = source.characters.toString()

        // Create a CharStream that reads from the code string
        val charStream = CharStreams.fromString(code)

        // Create a lexer that feeds off of input CharStream
        val lexer = FCTLexer(charStream)

        // Create a buffer of tokens pulled from the lexer
        val tokens = CommonTokenStream(lexer)

        // Create a parser that feeds off the tokens buffer
        val parser = FCTParser(tokens)

        // Parse the code starting from the generalBlock rule
        val mainContext = parser.generalBlock()

        // Convert the parsed ANTLR context to a CBSNode
        val rootNode = convertToCBSNode(mainContext)

        // Create a custom CBSRootNode that wraps the root CBSNode
        val cbsRootNode = CBSRootNode(this, rootNode)

        // Create and return a CallTarget for the RootNode
        return Truffle.getRuntime().createCallTarget(cbsRootNode)
    }

    override fun isObjectOfLanguage(`object`: Any?): Boolean {
        TODO("Not yet implemented")
    }

    private fun convertToCBSNode(context: FCTParser.GeneralBlockContext): CBSNode {
        // Assuming the context contains a single funconTerm or multiple statements
        val expr = context.funconTerm().expr()

        println(expr)

        TODO("Implement this please")
    }

}
