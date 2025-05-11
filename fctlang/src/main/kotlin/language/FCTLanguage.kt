package language

import builtin.ComplementTypeNode
import builtin.IntersectionTypeNode
import builtin.TermNode
import builtin.UnionTypeNode
import builtin.ValueNodeFactory.atomNode
import builtin.ValueNodeFactory.intNode
import builtin.ValueNodeFactory.strNode
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.nodes.Node
import fct.FCTLexer
import fct.FCTParser
import fct.FCTParser.*
import language.NodeFactory.createNode
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

        println("expected:")
        println("result-term: ${tests.resultTerm()?.expr()?.text}")
        val store = tests.store()?.expr()?.text
        if (store != null) println("store: $store")
        val standardOut = tests.standardOut()?.expr()?.text
        if (standardOut != null) println("standard-out: $standardOut")
        println()

        val rootNode = convertToFCTNode(mainContext)

        val inputNodes = processInput(inputs)

        val frameDescriptorBuilder = FrameDescriptor.newBuilder()
        // Add a frameslot for the entity map
        frameDescriptorBuilder.addSlots(1, FrameSlotKind.Object)
        val frameDescriptor = frameDescriptorBuilder.build()
        val fctRootNode = FCTRootNode(this, frameDescriptor, rootNode, inputNodes)
        return fctRootNode.callTarget
    }

    private fun processInput(inputs: InputsBlockContext?): Array<TermNode> {
        if (inputs == null) return emptyArray()

        val inputValue = inputs.standardIn().input()

        return when (inputValue) {
            is InputTupleContext -> inputValue.terminals().terminalValue().map { buildTree(it) }
            is InputListContext -> inputValue.terminals().terminalValue().map { buildTree(it) }
            is InputMapContext -> inputValue.termPairs().termPair().map { buildTree(it) }
            is InputSetContext -> inputValue.terminals().terminalValue().map { buildTree(it) }
            is InputValueContext -> listOf(buildTree(inputValue.terminalValue()))
            else -> listOf()
        }.toTypedArray()
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

                if (funconName == "atom") {
                    val expr = (parseTree.args() as SingleArgsContext).expr()
                    val value = (expr as SequenceExpressionContext).sequenceExpr().expr()[0].text
                    atomNode(value)
                } else {
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
                    createNode(funconName, children)
                }
            }

            is EmptySetContext -> {
                createNode("set", emptyList())
            }

            is ListExpressionContext -> {
                val elements = parseTree.listExpr().sequenceExpr()?.expr()?.map { buildTree(it) } ?: emptyList()
                createNode("list", elements)
            }

            is SetExpressionContext -> {
                val elements = parseTree.setExpr().sequenceExpr()?.expr()?.map { buildTree(it) } ?: emptyList()
                createNode("set", elements)
            }

            is MapExpressionContext -> {
                val pairs = parseTree.mapExpr().pairs()?.pair()?.map {
                    val key = buildTree(it.key)
                    val value = buildTree(it.value)
                    key to value
                } ?: emptyList()
                val elements = pairs.map { pair -> createNode("tuple", pair.toList()) }
                createNode("map", elements)
            }

            is TupleExpressionContext -> {
                val elements = parseTree.tupleExpr().sequenceExpr()?.expr()?.map { buildTree(it) } ?: emptyList()
                createNode("tuple", elements)
            }

            is StringContext -> {
                val str = parseTree.STRING().text
                strNode(str)
            }

            is NumberContext -> {
                val num = parseTree.NUMBER().text.toInt()
                intNode(num)
            }

            is TerminalExpressionContext -> buildTree(parseTree.terminalValue())
            is BinOpExpressionContext -> {
                val lhs = buildTree(parseTree.lhs)
                val rhs = buildTree(parseTree.rhs)
                when (parseTree.binOp().text) {
                    "|" -> UnionTypeNode(lhs, rhs)
                    "&" -> IntersectionTypeNode(lhs, rhs)
                    "=>" -> rhs
                    else -> throw IllegalArgumentException("Unsupported operation: ${parseTree.binOp().text}")
                }
            }

            is UnopExpressionContext -> {
                val operand = buildTree(parseTree.operand)
                when (parseTree.unOp().text) {
                    "~" -> ComplementTypeNode(operand)
                    "=>" -> operand
                    else -> throw IllegalArgumentException("Unsupported operation: ${parseTree.unOp().text}")
                }
            }

            is SequenceExpressionContext -> {
                val elements = parseTree.sequenceExpr()?.expr()?.map { buildTree(it) } ?: emptyList()
                createNode("sequence", elements)
            }

            else -> throw IllegalArgumentException("Unsupported expression type: ${parseTree::class.simpleName}: ${parseTree.text}")
        }
    }
}