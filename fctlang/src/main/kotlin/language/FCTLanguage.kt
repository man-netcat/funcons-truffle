package language

import builtin.ComplementTypeNode
import builtin.IntersectionTypeNode
import builtin.TermNode
import builtin.UnionTypeNode
import builtin.ValueNodeFactory.atomNode
import builtin.ValueNodeFactory.intNode
import builtin.ValueNodeFactory.strLiteralNode
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
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
        var entityFrameSlot = -1
    }

    override fun parse(request: ParsingRequest): CallTarget {

        val code = request.source.characters.toString()
        val parser = FCTParser(CommonTokenStream(FCTLexer(CharStreams.fromString(code))))
        val root = parser.root()

        val (funconTerm, inputs) = when (root) {
            is FCTFileContext -> root.expr() to emptyArray()
            is ConfigFileContext -> {
                val tests = root.testsBlock()
                println("expected:")
                println("result-term: ${tests.resultTerm()?.expr()?.text}")
                tests.store()?.expr()?.text?.let { println("store: $it") }
                tests.standardOut()?.expr()?.text?.let { println("standard-out: $it") }
                println()
                root.generalBlock().funconTerm() to processInput(root.inputsBlock())
            }

            else -> throw IllegalStateException("Unexpected file type")
        }

        val frameDescriptorBuilder = FrameDescriptor.newBuilder()
        entityFrameSlot = frameDescriptorBuilder.addSlot(FrameSlotKind.Object, null, null)
        val frameDescriptor = frameDescriptorBuilder.build()

        return FCTRootNode(this, frameDescriptor, buildTree(funconTerm), inputs).callTarget
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
                strLiteralNode(str)
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