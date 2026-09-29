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
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree

class FCTSyntaxException(message: String, cause: RecognitionException?) :
    RuntimeException(message, cause)

@TruffleLanguage.Registration(
    id = FCTLanguage.LANGUAGE_ID,
    name = "FCT Language",
    defaultMimeType = FCTLanguage.MIME_TYPE,
    characterMimeTypes = [FCTLanguage.MIME_TYPE]
)
class FCTLanguage : TruffleLanguage<FCTContext>() {
    companion object {
        const val LANGUAGE_ID = "fctlang"
        const val MIME_TYPE = "application/x-fctlang"
        var entitiesFrameslot: Int = -1
    }

    override fun parse(request: ParsingRequest): CallTarget {
        val code = request.source.characters.toString()
        val lexer = FCTLexer(CharStreams.fromString(code))
        val parser = FCTParser(CommonTokenStream(lexer))
        // ANTLR's default strategy recovers from syntax errors and hands back a partial
        // tree, so malformed input like `abstraction 12 |-> 13` silently evaluated to
        // `abstraction(12)`. Fail loudly instead of quietly evaluating something else.
        val errorListener = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: RecognitionException?
            ) {
                throw FCTSyntaxException("line $line:$charPositionInLine $msg", e)
            }
        }
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        val root = parser.root()

        val expectations = (root as? ConfigFileContext)?.testsBlock()
            ?.let { extractExpectations(it) }
            ?: TestExpectations.NONE

        val (funconTerm, inputs) = when (root) {
            is FCTFileContext -> root.expr() to emptyArray<TermNode>()
            is ConfigFileContext -> root.generalBlock().funconTerm() to processInput(root.inputsBlock())

            else -> throw IllegalStateException("Unexpected file type")
        }

        val frameDescriptorBuilder = FrameDescriptor.newBuilder()

        entitiesFrameslot = frameDescriptorBuilder.addSlot(FrameSlotKind.Object, null, null)

        if (entitiesFrameslot == -1) {
            throw IllegalStateException("Failed to create frame slot")
        }

        val frameDescriptor = frameDescriptorBuilder.build()

        return FCTRootNode(this, frameDescriptor, buildTree(funconTerm), inputs, expectations).callTarget
    }

    private fun sourceText(ctx: ParserRuleContext?): String? {
        if (ctx == null) return null
        val start = ctx.start
        val stop = ctx.stop ?: return ctx.text
        val stream = start.inputStream ?: return ctx.text
        return stream.getText(Interval.of(start.startIndex, stop.stopIndex))
    }

    private fun extractExpectations(tests: TestsBlockContext): TestExpectations = TestExpectations(
        resultTerm = sourceText(tests.resultTerm()?.expr()),
        store = sourceText(tests.store()?.expr()),
        standardOut = tests.standardOut()?.expr()?.let { expr ->
            if (expr is ListExpressionContext) {
                expr.listExpr().sequenceExpr()?.expr()?.mapNotNull { sourceText(it) } ?: emptyList()
            } else {
                null
            }
        }
    )

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
                    atomNode(value.unquoted())
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

/**
 * A STRING token's text carries its own delimiters, so `atom("@1")` arrives as
 * `"@1"`. The atom's name is the string's *content*, matching the reference,
 * which prints `atom(@1)` rather than `atom("@1")`. Without this, every atom
 * built by a config carries quotes inside its identity, and no atom without
 * quotes can be constructed at all.
 */
private fun String.unquoted(): String =
    if (length >= 2 && startsWith("\"") && endsWith("\"")) substring(1, length - 1) else this
