package interpreter

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.source.Source
import fct.FCTLexer
import fct.FCTParser
import fct.FCTParser.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

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

        private val REFERENCE: LanguageReference<FCTLanguage> =
            LanguageReference.create(FCTLanguage::class.java)

        fun get(node: Node): FCTLanguage {
            return REFERENCE.get(node)
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

    private fun convertToFCTNode(context: GeneralBlockContext): FCTNode {
        val expr = context.funconTerm()
        return buildTree(expr)
    }


    private fun buildTree(parseTree: ParseTree): FCTNode {
        return when (parseTree) {
            is FunconExpressionContext -> {
                val functionName = parseTree.name.text
                val children = when (val args = parseTree.args()) {
                    is MultipleArgsContext -> args.exprs().expr().map { buildTree(it) }
                    is SingleArgsContext -> listOf(buildTree(args.expr()))
                    is NoArgsContext -> emptyList()
                    else -> throw IllegalArgumentException("Unknown arg type: ${args::class.simpleName}, ${args.text}")
                }

                FCTNodeFactory.createNode(functionName, children)
            }

            is ListExpressionContext -> {
                val elements = parseTree.listExpr().exprs()?.expr()?.map { buildTree(it) } ?: emptyList()
                FCTNodeFactory.createNode("list", elements)
            }

            is SetExpressionContext -> {
                val elements = parseTree.setExpr().exprs()?.expr()?.map { buildTree(it) } ?: emptyList()
                FCTNodeFactory.createNode("set", elements)
            }

            is MapExpressionContext -> {
                val pairs = parseTree.mapExpr().pairs()?.pair()?.map {
                    val key = buildTree(it.key)
                    val value = buildTree(it.value)
                    key to value
                } ?: emptyList()
                val elements = pairs.map { pair -> FCTNodeFactory.createNode("tuple", pair.toList()) }
                FCTNodeFactory.createNode("map", elements)
            }

            is TupleExpressionContext -> {
                val elements = parseTree.tupleExpr().exprs()?.expr()?.map { buildTree(it) } ?: emptyList()
                FCTNodeFactory.createNode("tuple", elements)
            }

            is TerminalExpressionContext -> {
                when (val terminal = parseTree.terminalValue()) {
                    is StringContext -> {
                        val str = terminal.STRING().text.removeSurrounding("\"").toStringNode()
                        FCTNodeFactory.createNode("string", listOf(str))
                    }

                    is NumberContext -> {
                        val num = terminal.NUMBER().text.toInt().toIntegerNode()
                        FCTNodeFactory.createNode("integer", listOf(num))
                    }

                    is EmptyContext -> FCTNodeFactory.createNode("null-value", emptyList())
                    else -> throw IllegalArgumentException("Unknown terminal value: $terminal")
                }
            }

            else -> throw IllegalArgumentException("Unsupported expression type: ${parseTree::class.simpleName}")
        }
    }
}
