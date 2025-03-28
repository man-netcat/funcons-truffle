package language

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.nodes.Node
import fct.FCTLexer
import fct.FCTParser
import fct.FCTParser.*
import generated.StandardInNode
import language.Util.DEBUG
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

        val root = parser.root()

        val mainContext = root.generalBlock()
        val tests = root.testsBlock()
        val inputs = root.inputsBlock()

        if (DEBUG) {
            println("expected:")
            println("result-term: ${tests.resultTerm().text}")
            println("standard-out: ${tests.standardOut().text}")
            println()
        }


        val rootNode = convertToFCTNode(mainContext)

        processInput(inputs, rootNode)

        val frameDescriptorBuilder = FrameDescriptor.newBuilder()
        // TODO: 100 slots is probably too many
        frameDescriptorBuilder.addSlots(100, FrameSlotKind.Object)
        val frameDescriptor = frameDescriptorBuilder.build()
        val fctRootNode = FCTRootNode(this, frameDescriptor, rootNode)
        return fctRootNode.callTarget
    }

    private fun processInput(inputs: InputsBlockContext?, rootNode: TermNode) {
        if (inputs == null) return

        val inputValue = inputs.standardIn().input()

        val inputNodes = when (inputValue) {
            is InputTupleContext -> inputValue.terminals().terminalValue().map { buildTree(it) }
            is InputListContext -> inputValue.terminals().terminalValue().map { buildTree(it) }
            is InputMapContext -> inputValue.termPairs().termPair().map { buildTree(it) }
            is InputSetContext -> inputValue.terminals().terminalValue().map { buildTree(it) }
            is InputValueContext -> listOf(buildTree(inputValue.terminalValue()))
            else -> listOf()
        }.toTypedArray()

        val standardIn = StandardInNode(SequenceNode(*inputNodes))

        rootNode.appendGlobal("standard-in", standardIn)
    }

    override fun createContext(env: Env): FCTContext {
        return FCTContext(env)
    }

    private fun convertToFCTNode(context: GeneralBlockContext): TermNode {
        val expr = context.funconTerm()
        return buildTree(expr)
    }

    private fun buildTree(parseTree: ParseTree): TermNode {
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

            is StringContext -> {
                val str = parseTree.STRING().text
                FCTNodeFactory.createNode("string", listOf(str))
            }

            is NumberContext -> {
                val num = parseTree.NUMBER().text.toInt()
                FCTNodeFactory.createNode("integer", listOf(num))
            }

            is TerminalExpressionContext -> buildTree(parseTree.terminalValue())

            else -> throw IllegalArgumentException("Unsupported expression type: ${parseTree::class.simpleName}")
        }
    }
}