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
        rootTerm.appendGlobal("standard-in", standardInNode)
        val reduced = rootTerm.rewrite(frame)
        val resultTerm = listOf(reduced.value.toString())
        val standardOutNode = reduced.getGlobal("standard-out")
        val elements = standardOutNode.elements
        val standardOutValues = elements.map { it.value.toString() }

        val res = (resultTerm + standardOutValues).toTypedArray()
        return ResultArray(res)
    }
}
