package main.objects

import cbs.CBSParser.*
import main.*
import main.dataclasses.Param
import main.funconOf
import objects.DatatypeFunconObject
import org.antlr.v4.runtime.tree.ParseTree

/**
 * One funcon, type, datatype or entity, and the code generated for it.
 *
 * @param registry the whole corpus, which a definition's own body refers to: a
 * rule may name any funcon, not only the ones in the file that declared it.
 */
abstract class Object(
    val ctx: ParseTree,
    metaVariables: Set<Pair<ExprContext, ExprContext>>,
    protected val registry: Registry,
) {
    private val funcon = funconOf(ctx)

    val metavariableMap = metaVariables.associate { (variable, type) -> variable.text to type }

    /**
     * True for a funcon Truffle implements itself, so no node class is written.
     *
     * `print` reads a stream and `atomic` cannot be a reduction, so a generated
     * class would not be a working implementation of either. A datatype's
     * constructor is built-in when the datatype it belongs to is.
     */
    val builtin: Boolean by lazy {
        registry.isBuiltin(name) ||
            when (ctx) {
                is FunconDefinitionContext -> ctx.modifier?.text == "Built-in"
                is TypeDefinitionContext -> ctx.modifier?.text == "Built-in"
                is DatatypeDefinitionContext -> ctx.modifier?.text == "Built-in"
                else -> false
            } || (this is DatatypeFunconObject && this.superclass.builtin)
    }

    val name: String get() = funcon.name
    val aliases: List<String> get() = funcon.aliases
    val params: List<Param> get() = funcon.params

    val dependencies = mutableSetOf<Object>()
    open val contentStr: String = ""
    open val superClassStr: String = emptySuperClass(TERMNODE)
    open val keyWords: List<String> = emptyList()

    /**
     * The `isIn<T>()` method a type or datatype gets, emitted after its class.
     *
     * Only a type can be a member of a type, so a funcon has none and this is
     * empty. It is a property of the object rather than a case in the caller
     * because there is nothing here to distinguish a type from a datatype.
     */
    open val elementInBody: String = ""

    // Read once per rewrite, and a rewrite is generated for every pattern
    // occurrence, so a funcon with many rules would re-scan its parameters
    // thousands of times over.
    val sequenceIndex: Int by lazy { params.indexOfFirst { it.type.isSequence } }
    val hasSequence: Boolean by lazy { sequenceIndex >= 0 }
    val sequenceParam: Param? by lazy { if (hasSequence) params[sequenceIndex] else null }

    /**
     * The name of each constructor parameter, in order.
     *
     * The constructor and the [nodeMetaEntry] written beside it both read this, so the two cannot
     * disagree about what the parameters are called.
     */
    val paramNames: List<String> by lazy {
        params.map { param ->
            if (this is TypeObject || this is AlgebraicDatatypeObject) "${asVarName}Tp${param.index}"
            else "p${param.index}"
        }
    }

    private val paramStrs: List<String> by lazy {
        params.mapIndexed { index, param ->
                val annotation = makeAnnotation(
                    isVararg = param.type.isVararg,
                    isEntity = this !is EntityObject,
                    isEager = !param.type.computes
            )
                val paramTypeStr = if (param.type.isSequence) SEQUENCE else TERMNODE
            val default = "SequenceNode()"
            makeParam(paramTypeStr, paramNames[index], annotation, default)
        }
    }

    private val interfaceProperties: List<String> by lazy {
        val valueParams = params.filterNot { param -> param.valueExpr == null }

        valueParams.map { param ->
            val paramTypeStr = if (param.type.isSequence) {
                if (param.type.isVararg) {
                    "Array<out $SEQUENCE>"
                } else SEQUENCE
            } else TERMNODE
            makeVariable("p${param.index}", type = paramTypeStr)
        }
    }

    val camelCaseName = toCamelCase(name)
    val nodeName = toNodeName(name)
    val interfaceName = toInterfaceName(name)
    val asVarName = toVariableName(name)

    /** The name of the class [makeCode] writes, which is not a node class for an entity. */
    val className: String by lazy { if (this is EntityObject) entityName else nodeName }

    /**
     * This object's entry in the generated node metadata map, or null when it writes no class.
     *
     * The map is what lets a generated node be created and read without reflection, so it has to
     * describe the same constructor the class is given: the arguments in order, which of them are
     * passed by value, and which is the sequence one. A generated constructor takes only `TermNode`s
     * and no literals, so the children are all of its arguments, and an argument declared as a
     * `SequenceNode` has to be cast back to one when the node is rebuilt.
     */
    val nodeMetaEntry: String? by lazy {
        if (builtin || this is EntityObject) {
            null
        } else {
            val count = params.size
            val eager = params.mapIndexedNotNull { index, param ->
                if (!param.type.computes) index else null
            }
            val eagerSet = if (eager.isEmpty()) "emptySet()" else eager.joinToString(", ", "setOf(", ")")

            val children = if (count == 0) {
                "emptyList()"
            } else {
                "val node = n as $className; listOf(${paramNames.joinToString(", ") { "node.$it" }})"
            }
            val build = if (count == 0) {
                "$className()"
            } else {
                val args = params.mapIndexed { index, param ->
                    val type = if (param.type.isSequence) SEQUENCE else TERMNODE
                    "a[$index] as $type"
                }
                "$className(${args.joinToString(", ")})"
            }

            """"generated.$className" to { GeneratedNodeMeta($count, $sequenceIndex, $eagerSet, { n -> $children }, { a -> $build }) }"""
        }
    }

    fun makeCode(): String {
        return if (!builtin) makeClass(
            className,
            content = contentStr,
            constructorArgs = paramStrs,
            superClass = superClassStr,
            keywords = keyWords,
        ) else makeInterface(interfaceName, properties = interfaceProperties)
    }
}
