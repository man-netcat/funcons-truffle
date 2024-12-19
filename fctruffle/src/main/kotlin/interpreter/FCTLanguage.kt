package interpreter

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.Source
import fct.FCTParser
import fct.FCTLexer
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.lang.reflect.Constructor

@TruffleLanguage.Registration(
    id = FCTLanguage.ID,
    name = "fctlang",
    defaultMimeType = FCTLanguage.MIME_TYPE,
    characterMimeTypes = [FCTLanguage.MIME_TYPE]
)
class FCTLanguage : TruffleLanguage<FCTContext>() {
    companion object {
        const val ID: String = "fctlang"
        const val MIME_TYPE: String = "application/x-fctlang"
        private val LANGUAGE_REFERENCE: LanguageReference<FCTLanguage> =
            LanguageReference.create(FCTLanguage::class.java)

        fun get(node: Node?): FCTLanguage {
            return if (node != null) {
                LANGUAGE_REFERENCE.get(node)
            } else {
                throw IllegalStateException("Node cannot be null when calling get on the fast-path.")
            }
        }
    }

    override fun createContext(env: Env): FCTContext {
        return FCTContext(env)
    }

    fun parseSource(source: Source): CallTarget {
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

        // Return a CallTarget for the RootNode
        return fctRootNode.callTarget
    }

    private fun toClassName(funconName: String): String {
        return "generated." + funconName.split('-').joinToString("") { it.replaceFirstChar(Char::titlecase) } + "Node"
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
