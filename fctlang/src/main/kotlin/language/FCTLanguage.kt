package language

import builtin.*
import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind
import com.oracle.truffle.api.nodes.Node
import fct.FCTLexer
import fct.FCTParser
import fct.FCTParser.*
import generated.aliasMap
import language.Util.DEBUG
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

private const val GENERATED = "generated"
private const val INTERPRETER = "language"
private const val BUILTIN = "builtin"

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
                    AtomNode(value)
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
                createNode("string", listOf(toCharSequence(str)))
            }

            is NumberContext -> {
                val num = parseTree.NUMBER().text.toInt()
                createNode("integer", listOf(num))
            }

            is TerminalExpressionContext -> buildTree(parseTree.terminalValue())
            is BinOpExpressionContext -> {
                val lhs = buildTree(parseTree.lhs)
                val rhs = buildTree(parseTree.rhs)
                when (parseTree.binOp().text) {
                    "|" -> UnionTypeNode(lhs, rhs)
                    "&" -> IntersectionTypeNode(lhs, rhs)
                    else -> throw IllegalArgumentException("Unsupported operation: ${parseTree.binOp().text}")
                }
            }

            is UnopExpressionContext -> {
                val operand = buildTree(parseTree.operand)
                when (parseTree.unOp().text) {
                    "~" -> ComplementTypeNode(operand)
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

    private fun createNode(funconName: String, args: List<Any>): TermNode {
        val resolvedName = aliasMap[funconName] ?: funconName

        val classNames = sequenceOf(GENERATED, INTERPRETER, BUILTIN)
            .map { packageName -> toClassName(resolvedName, packageName) }

        val clazz = classNames
            .mapNotNull { className ->
                runCatching { Class.forName(className).kotlin }.getOrNull()
            }
            .firstOrNull() ?: throw ClassNotFoundException("No class found for $funconName")

        val constructor = clazz.constructors.first()

        if (DEBUG) {
            println("creating node: ${toNodeName(funconName)} with children ${args.map { it::class.simpleName }}")
            println("Constructor for ${clazz.simpleName}: ${constructor.parameters.map { it.type.toString() }}")
        }

        val argsMap = prepareArguments(constructor, args)
        return constructor.callBy(argsMap) as TermNode
    }

    private fun prepareArguments(
        constructor: KFunction<Any>,
        args: List<Any>,
    ): Map<KParameter, Any?> {
        val parameters = constructor.parameters
        val varargIndex = parameters.indexOfFirst { it.isVararg }
        val sequenceIndex = parameters.indexOfFirst { it.type.classifier == SequenceNode::class }

        return if (varargIndex >= 0) {
            val beforeVararg = parameters.take(varargIndex)
            val varargParam = parameters[varargIndex]
            val afterVararg = parameters.drop(varargIndex + 1)

            val beforeArgs = args.take(beforeVararg.size)
            val varargArgs = args.drop(beforeVararg.size).dropLast(afterVararg.size)
                .toTypedArray()
            val afterArgs = args.takeLast(afterVararg.size)


            val componentType = (varargParam.type.arguments.first().type!!.classifier as KClass<*>).java
            val varargArray = if (varargArgs.isNotEmpty()) {
                java.lang.reflect.Array.newInstance(componentType, varargArgs.size).also { array ->
                    varargArgs.withIndex().forEach { arg ->
                        val value = if (componentType == SequenceNode::class.java) {
                            createNode("sequence", listOf(arg.value))
                        } else arg.value
                        java.lang.reflect.Array.set(array, arg.index, value)
                    }
                }
            } else java.lang.reflect.Array.newInstance(componentType, 0)

            beforeVararg.zip(beforeArgs).toMap() +
                    mapOf(varargParam to varargArray) +
                    afterVararg.zip(afterArgs).toMap()
        } else if (sequenceIndex >= 0) {
            val beforeSequence = parameters.take(sequenceIndex)
            val sequenceParam = parameters[sequenceIndex]
            val afterSequence = parameters.drop(sequenceIndex + 1)

            val beforeArgs = args.take(beforeSequence.size)
            val sequenceArgs = args.drop(beforeSequence.size).dropLast(afterSequence.size)
                .toTypedArray()
            val afterArgs = args.takeLast(afterSequence.size)
            val sequence = createNode("sequence", sequenceArgs.filterIsInstance<TermNode>())

            beforeSequence.zip(beforeArgs).toMap() +
                    (sequenceParam to sequence) +
                    afterSequence.zip(afterArgs).toMap()
        } else {
            parameters.zip(args).toMap()
        }
    }

    private fun toNodeName(funconName: String): String {
        return "${funconName.split('-').joinToString("") { it.replaceFirstChar(Char::titlecase) }}Node"
    }

    private fun toClassName(funconName: String, packageName: String): String {
        return "$packageName.${toNodeName(funconName)}"
    }
}