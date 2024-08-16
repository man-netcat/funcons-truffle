package trufflegen.parsetests

import trufflegen.generated.*
import org.junit.jupiter.api.Test

class BooleansTest : BaseTest() {
    private val trueNode = BooleansNode("true")
    private val falseNode = BooleansNode("false")

    @Test
    fun notNodeTest() {
        val notNode = NotNode(falseNode)
        performTest(notNode, "true")
    }

    @Test
    fun andNodeTest() {
        val andNode = AndNode(trueNode, trueNode, falseNode, trueNode)
        performTest(andNode, "false")
    }

    @Test
    fun orNodeTest() {
        val orNode = OrNode(falseNode, falseNode, trueNode, falseNode)
        performTest(orNode, "true")
    }

    @Test
    fun xorNodeTest() {
        val xorNode = ExclusiveOrNode(trueNode, falseNode)
        performTest(xorNode, "true")
    }

    @Test
    fun impliesNodeTest() {
        val impliesNode = ImpliesNode(trueNode, falseNode)
        performTest(impliesNode, "false")
    }

    @Test
    fun andOrNodeTest() {
        val andOrNode = OrNode(falseNode, AndNode(trueNode, falseNode, trueNode), trueNode)
        performTest(andOrNode, "true")
    }
}