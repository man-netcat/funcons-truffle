import com.trufflegen.generated.*
import com.trufflegen.parsetests.BaseTest
import org.junit.jupiter.api.Test

class BooleansTest : BaseTest() {
    private val trueNode = BooleansNode("true")
    private val falseNode = BooleansNode("false")

    @Test
    fun notNodeTest() {
        val notNode = NotNode(falseNode)
        baseTest(notNode, "true")
    }

    @Test
    fun andNodeTest() {
        val andNode = AndNode(trueNode, trueNode, falseNode, trueNode)
        baseTest(andNode, "false")
    }

    @Test
    fun orNodeTest() {
        val orNode = OrNode(falseNode, falseNode, trueNode, falseNode)
        baseTest(orNode, "true")
    }

    @Test
    fun xorNodeTest() {
        val xorNode = ExclusiveOrNode(trueNode, falseNode)
        baseTest(xorNode, "true")
    }

    @Test
    fun impliesNodeTest() {
        val impliesNode = ImpliesNode(trueNode, falseNode)
        baseTest(impliesNode, "false")
    }

    @Test
    fun andOrNodeTest() {
        val andOrNode = OrNode(falseNode, AndNode(trueNode, falseNode, trueNode), trueNode)
        baseTest(andOrNode, "true")
    }
}