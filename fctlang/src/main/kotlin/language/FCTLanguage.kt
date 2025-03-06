package language

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.nodes.Node
import fct.FCTLexer
import fct.FCTParser
import fct.FCTParser.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

@TruffleLanguage.Registration(
    id = "fctlang",
    name = "FCT Language",
    defaultMimeType = "application/x-fctlang",
    characterMimeTypes = ["application/x-fctlang"]
)
class FCTLanguage : TruffleLanguage<FCTContext>() {
    companion object {
        private val REFERENCE: LanguageReference<FCTLanguage> =
            LanguageReference.create(FCTLanguage::class.java)

        fun get(node: Node): FCTLanguage {
            return REFERENCE.get(node)
        }
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val source = request.source
        val code = source.characters.toString()

        val charStream = CharStreams.fromString(code)
        val lexer = FCTLexer(charStream)
        val tokens = CommonTokenStream(lexer)
        val parser = FCTParser(tokens)
        val mainContext = parser.generalBlock()
        val rootNode = convertToFCTNode(mainContext)
        val frameDescriptorBuilder = FrameDescriptor.newBuilder()
        frameDescriptorBuilder.addSlots(100, FrameSlotKind.Object)
        val frameDescriptor = frameDescriptorBuilder.build()
        val fctRootNode = FCTRootNode(this, frameDescriptor, rootNode)
        return fctRootNode.callTarget
    }

    override fun createContext(env: Env): FCTContext {
        return FCTContext(env)
    }

    private fun convertToFCTNode(context: GeneralBlockContext): FCTNode {
        val expr = context.funconTerm()
        return buildTree(expr)
    }

    private fun buildTree(parseTree: ParseTree): FCTNode {
        return when (parseTree) {
            is FunconTermContext -> {
                buildTree(parseTree.expr())
            }

            is FunconExpressionContext -> {
                val funconName = parseTree.name.text

                val args = parseTree.args()
                val children = when (args) {
                    is SingleArgsContext -> {
                        when (val arg = args.expr()) {
                            is SequenceExpressionContext -> arg.sequenceExpr().expr()
                                .map { expr -> buildTree(expr) }

                            else -> listOf(buildTree(arg))
                        }
                    }

                    is NoArgsContext -> emptyList()
                    else -> throw IllegalArgumentException("Unknown arg type: ${args::class.simpleName}, ${args.text}")
                }
                FCTNodeFactory.createNode(funconName, children)
            }

            is ListExpressionContext -> {
                val elements = parseTree.listExpr().sequenceExpr()?.expr()?.map { buildTree(it) } ?: emptyList()
                FCTNodeFactory.createNode("list", elements)
            }

            is SetExpressionContext -> {
                val elements = parseTree.setExpr().sequenceExpr()?.expr()?.map { buildTree(it) } ?: emptyList()
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
                val elements = parseTree.tupleExpr().sequenceExpr()?.expr()?.map { buildTree(it) } ?: emptyList()
                FCTNodeFactory.createNode("tuple", elements)
            }

            is TerminalExpressionContext -> {
                when (val terminal = parseTree.terminalValue()) {
                    is StringContext -> {
                        val str = terminal.STRING().text.removeSurrounding("\"")
                        FCTNodeFactory.createNode("string", listOf(str))
                    }

                    is NumberContext -> {
                        val num = terminal.NUMBER().text.toInt()
                        FCTNodeFactory.createNode("integer", listOf(num))
                    }

                    else -> throw IllegalArgumentException("Unknown terminal value: ${terminal.text}")
                }
            }

            else -> throw IllegalArgumentException("Unsupported expression type: ${parseTree::class.simpleName}")
        }
    }
}