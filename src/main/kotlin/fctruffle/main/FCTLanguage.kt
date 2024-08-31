package fctruffle.main

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.source.Source
import fctruffle.antlr.FCTLexer
import fctruffle.antlr.FCTParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.lang.reflect.Constructor

@TruffleLanguage.Registration(
    id = FCTLanguage.ID,
    name = "fctlang",
    defaultMimeType = FCTLanguage.MIME_TYPE,
    characterMimeTypes = [FCTLanguage.MIME_TYPE]
)
class FCTLanguage : TruffleLanguage<Nothing>() {
    companion object {
        const val ID: String = "fctlang"
        const val MIME_TYPE: String = "application/x-fctlang"
    }

    override fun createContext(env: Env?): Nothing {
        TODO("Not yet implemented")
    }

    fun parse(source: Source): CallTarget {
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

        // Convert the parsed ANTLR context to a FCTNode
        val rootNode = convertToFCTNode(mainContext)

        // Create a custom FCTRootNode that wraps the root FCTNode
        val fctRootNode = FCTRootNode(this, rootNode)

        // Create and return a CallTarget for the RootNode
        return Truffle.getRuntime().createCallTarget(fctRootNode)
    }

    override fun isObjectOfLanguage(`object`: Any?): Boolean {
        TODO("Not yet implemented")
    }

    private fun toClassName(funconName: String): String {
        return "fctruffle.generated." + funconName.split('-')
            .joinToString("") { it.replaceFirstChar(Char::titlecase) } + "Node"
    }

    // Function to instantiate the class from its name
    private fun instantiateClass(className: String): Any? {
        // Load the class by name
        val clazz = Class.forName(className)

        // Get the default constructor
        val constructor: Constructor<*> = clazz.getConstructor()

        // Create an instance using the constructor
        return constructor.newInstance()
    }

    private fun convertToFCTNode(context: FCTParser.GeneralBlockContext): FCTNode {
        val expr = context.funconTerm()

        // Extract the funconName
        val funconName = expr.funcon().funconName().text

        // Convert the funconName to a class name
        val className = toClassName(funconName)

        // Instantiate the class
        val fctNode = instantiateClass(className) as? FCTNode

        return fctNode ?: throw IllegalArgumentException("Class $className could not be instantiated")
    }
}
