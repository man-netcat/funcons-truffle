package builtin

import generated.nodeMetas
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KFunction1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Reflection metadata for a node class.
 *
 * Everything here is a property of the *class*, not of any instance: the constructor shape, which
 * arguments are term children, which are eager, and which argument position holds a sequence. The
 * per-node constructor used to redo all of it for every node it created, including a walk of the
 * whole supertype hierarchy, so this is computed once per node class and shared.
 *
 * There are two implementations. A generated node class is described by the metadata `trufflegen`
 * writes alongside it, because the generator already knows everything below when it writes the
 * constructor; only the classes Truffle implements by hand are read reflectively, and those are
 * few enough that the cost does not show.
 */
internal interface NodeMeta {
    /** How many arguments the primary constructor takes. */
    val paramCount: Int

    /** Index of the argument that holds a [SequenceNode], or -1 when the node takes none. */
    val sequenceParamIndex: Int

    /** Constructor arguments that are [TermNode]s, in declaration order. */
    fun childrenOf(node: TermNode): List<TermNode>

    /** All constructor arguments, in declaration order, keeping non-node values such as literals. */
    fun argValuesOf(node: TermNode): List<Any?>

    fun newInstance(args: Array<out Any?>): TermNode

    fun isEager(index: Int): Boolean
}

/**
 * The metadata of a node class that is written by hand, read off the class reflectively.
 *
 * Every constructor parameter has to be readable as a property of the same name, which is what
 * makes a constructor parameter and a property of one name a hazard rather than a convenience: see
 * [SequenceNode], where the parameter is `terms` and the property is `elements`.
 */
@Suppress("UNCHECKED_CAST")
internal class ReflectiveNodeMeta(kClass: KClass<out TermNode>) : NodeMeta {
    private val primaryCtor: KFunction<TermNode> = kClass.primaryConstructor!!

    override val paramCount: Int = primaryCtor.parameters.size

    override val sequenceParamIndex: Int =
        primaryCtor.parameters.indexOfFirst { it.type.classifier == SequenceNode::class }

    private val argGetters: List<KFunction1<TermNode, Any?>> = primaryCtor.parameters.map { param ->
        kClass.memberProperties.first { it.name == param.name }.getter as KFunction1<TermNode, Any?>
    }

    private val eagerIndices: Set<Int> = primaryCtor.parameters.mapIndexedNotNull { index, param ->
        if (param.findAnnotation<TermNode.Eager>() != null) index else null
    }.toSet()

    override fun childrenOf(node: TermNode): List<TermNode> = argGetters.mapNotNull { it(node) as? TermNode }

    override fun argValuesOf(node: TermNode): List<Any?> = argGetters.mapNotNull { it(node) }

    override fun newInstance(args: Array<out Any?>): TermNode = primaryCtor.call(*args) as TermNode

    override fun isEager(index: Int): Boolean = index in eagerIndices
}

/**
 * The metadata `trufflegen` writes for a node class it generated.
 *
 * Each piece is passed in rather than computed, so reading it is a field access and a call, where
 * [ReflectiveNodeMeta] would walk the constructor and invoke a getter reflectively. [children] and
 * [build] are lambdas over the node's own properties and constructor, which is why a generated
 * class gets this and a hand-written one does not.
 */
internal class GeneratedNodeMeta(
    override val paramCount: Int,
    override val sequenceParamIndex: Int,
    private val eagerIndices: Set<Int>,
    private val children: (TermNode) -> List<TermNode>,
    private val build: (Array<out Any?>) -> TermNode,
) : NodeMeta {
    /**
     * A generated constructor takes only [TermNode]s, so the children are all of its arguments and
     * this is the same list. The cast is unchecked because it is one: the only value type in the
     * list is a `TermNode` either way.
     */
    @Suppress("UNCHECKED_CAST")
    override fun argValuesOf(node: TermNode): List<Any?> = children(node) as List<Any?>

    override fun childrenOf(node: TermNode): List<TermNode> = children(node)

    override fun newInstance(args: Array<out Any?>): TermNode = build(args)

    override fun isEager(index: Int): Boolean = index in eagerIndices
}

internal object NodeMetas {
    private val byClass = object : ClassValue<NodeMeta>() {
        // Only reached the first time a node of the class is created, so the map lookup that
        // decides whether the class is generated is paid once per class rather than per node.
        override fun computeValue(type: Class<*>): NodeMeta =
            nodeMetas[type.name]?.invoke() ?: ReflectiveNodeMeta(type.kotlin as KClass<out TermNode>)
    }

    fun of(node: TermNode): NodeMeta = byClass.get(node.javaClass)
}
