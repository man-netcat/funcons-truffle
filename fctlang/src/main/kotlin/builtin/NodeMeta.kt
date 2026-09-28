package builtin

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
 */
@Suppress("UNCHECKED_CAST")
internal class NodeMeta(kClass: KClass<out TermNode>) {
    val primaryCtor: KFunction<TermNode> = kClass.primaryConstructor!!

    val paramCount: Int = primaryCtor.parameters.size

    /** Index of the argument that holds a [SequenceNode], or -1 when the node takes none. */
    val sequenceParamIndex: Int =
        primaryCtor.parameters.indexOfFirst { it.type.classifier == SequenceNode::class }

    private val argGetters: List<KFunction1<TermNode, Any?>> = primaryCtor.parameters.map { param ->
        kClass.memberProperties.first { it.name == param.name }.getter as KFunction1<TermNode, Any?>
    }

    private val eagerIndices: Set<Int> = primaryCtor.parameters.mapIndexedNotNull { index, param ->
        if (param.findAnnotation<TermNode.Eager>() != null) index else null
    }.toSet()

    /** Constructor arguments that are [TermNode]s, in declaration order. */
    fun childrenOf(node: TermNode): List<TermNode> = argGetters.mapNotNull { it(node) as? TermNode }

    /** All constructor arguments, in declaration order, keeping non-node values such as literals. */
    fun argValuesOf(node: TermNode): List<Any?> = argGetters.mapNotNull { it(node) }

    fun newInstance(args: Array<out Any?>): TermNode = primaryCtor.call(*args) as TermNode

    fun isEager(index: Int): Boolean = index in eagerIndices
}

internal object NodeMetas {
    private val byClass = object : ClassValue<NodeMeta>() {
        override fun computeValue(type: Class<*>): NodeMeta =
            NodeMeta(type.kotlin as KClass<out TermNode>)
    }

    fun of(node: TermNode): NodeMeta = byClass.get(node.javaClass)
}
