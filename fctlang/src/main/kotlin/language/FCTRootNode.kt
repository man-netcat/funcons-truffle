package language

import builtin.SequenceNode
import builtin.TermNode
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class FCTRootNode(
    language: FCTLanguage,
    frameDescriptor: FrameDescriptor,
    @Child private var rootTerm: TermNode,
    val inputNodes: Array<TermNode>,
) : RootNode(language, frameDescriptor) {
    override fun execute(frame: VirtualFrame): ResultArray {
        val standardInNode = SequenceNode(*inputNodes)
        appendEntity(frame, "standard-in", standardInNode)
        val resultTerm = rootTerm.rewrite(frame)
        val resultValue = listOf(resultTerm.toString())
        val standardOutNode = getEntity(frame, "standard-out")
        val storeNode = getEntity(frame, "store")
        val storeValue = listOf(storeNode.toString())
        val elements = standardOutNode.elements
        val standardOutValues = elements.map { it.toString() }

        val res = (resultValue + storeValue + standardOutValues).toTypedArray()
        return ResultArray(res)
    }
}
