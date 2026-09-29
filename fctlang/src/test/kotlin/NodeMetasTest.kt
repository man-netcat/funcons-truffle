package builtin

import generated.nodeMetas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * The generated node metadata has to say what reflection says.
 *
 * `trufflegen` writes `nodeMetas` because it knows each node's constructor when it writes the
 * class, but the hand-written node classes are still read reflectively, and the reduction path
 * reads metadata through the same interface either way. A generator that omits a constructor
 * argument, or marks the wrong one eager, would therefore not fail to compile and would not
 * obviously fail at run time either: it would build the wrong node, or reduce one argument when it
 * meant to reduce another.
 *
 * So this compares the two directly, for every generated class: the shape, the children, and what
 * rebuilding produces. Reflection is the oracle, and it is still here to be compared against.
 */
class NodeMetasTest {

    @Test
    fun generatedMetadataTest() {
        assertTrue(nodeMetas.size > 100, "expected the whole corpus, found ${nodeMetas.size} classes")

        val mismatches = mutableListOf<String>()

        nodeMetas.forEach { (name, makeMeta) ->
            val kClass = Class.forName(name).kotlin as KClass<out TermNode>
            val generatedMeta = makeMeta()
            val reflectiveMeta = ReflectiveNodeMeta(kClass)

            if (generatedMeta.paramCount != reflectiveMeta.paramCount) {
                mismatches += "$name: paramCount ${generatedMeta.paramCount} != ${reflectiveMeta.paramCount}"
                return@forEach
            }
            if (generatedMeta.sequenceParamIndex != reflectiveMeta.sequenceParamIndex) {
                mismatches += "$name: sequenceParamIndex ${generatedMeta.sequenceParamIndex} != " +
                    reflectiveMeta.sequenceParamIndex
            }
            for (index in 0 until generatedMeta.paramCount) {
                if (generatedMeta.isEager(index) != reflectiveMeta.isEager(index)) {
                    mismatches += "$name: isEager($index) ${generatedMeta.isEager(index)} != " +
                        reflectiveMeta.isEager(index)
                }
            }

            // Every generated parameter has a default, so the no-argument call is an instance of
            // the class with the same shape for both. `callBy` is what makes that legal: a plain
            // `call` would demand a value for each parameter.
            val instance = kClass.primaryConstructor!!.callBy(emptyMap()) as TermNode

            val generatedChildren = generatedMeta.childrenOf(instance)
            val reflectiveChildren = reflectiveMeta.childrenOf(instance)
            if (generatedChildren.size != reflectiveChildren.size) {
                mismatches += "$name: children ${generatedChildren.size} != ${reflectiveChildren.size}"
                return@forEach
            }
            // The same instances, not merely equal ones: the reduction path compares children by
            // reference to decide what to reduce, so a copy would reduce a term that is not in the
            // node.
            generatedChildren.indices.forEach { index ->
                if (generatedChildren[index] !== reflectiveChildren[index]) {
                    mismatches += "$name: child $index is a different object"
                }
            }

            val rebuilt = generatedMeta.newInstance(generatedChildren.toTypedArray())
            val reflectivelyRebuilt = reflectiveMeta.newInstance(reflectiveChildren.toTypedArray())
            if (rebuilt != reflectivelyRebuilt) {
                mismatches += "$name: rebuild gives $rebuilt, reflection gives $reflectivelyRebuilt"
            }
        }

        assertEquals(emptyList<String>(), mismatches, "generated metadata disagrees with reflection")
    }

    /**
     * A node class the generator did not write still has to be described, or the interpreter would
     * build nothing for the funcons Truffle implements by hand.
     */
    @Test
    fun handWrittenNodeMetadataTest() {
        listOf(AtomicNode(SequenceNode()), ReadNode(), SequenceNode()).forEach { node ->
            assertTrue(
                NodeMetas.of(node) is ReflectiveNodeMeta,
                "${node.javaClass.simpleName} should be read reflectively",
            )
        }
    }

    /**
     * The metadata is looked up per class and then reused, so it has to be the same object each
     * time. A map that built a new one per node would move the cost it exists to remove.
     */
    @Test
    fun metadataIsComputedOncePerClassTest() {
        val node = generated.FinallyNode(SequenceNode(), SequenceNode())
        val first = NodeMetas.of(node)
        val second = NodeMetas.of(node)

        assertSame(first, second)
    }
}
