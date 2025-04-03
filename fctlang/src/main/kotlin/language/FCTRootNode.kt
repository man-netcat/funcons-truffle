package language

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import generated.StandardInNode
import generated.StandardOutNode

class FCTRootNode(
    language: FCTLanguage,
    frameDescriptor: FrameDescriptor,
    @Child private var rootTerm: TermNode,
    val inputNodes: Array<TermNode>,
) : RootNode(language, frameDescriptor) {
    override fun execute(frame: VirtualFrame): ResultArray {
        val standardInNode = StandardInNode(SequenceNode(*inputNodes))
        rootTerm.appendGlobal("standard-in", standardInNode)
        rootTerm.rewrite(frame)
        val resultTerm = listOf(rootTerm.value.toString())
        val standardOutNode = rootTerm.getGlobal("standard-out") as? StandardOutNode
        val elements = standardOutNode?.p0?.elements.orEmpty()
        val standardOutValues = elements.map { it.value.toString() }

        val res = (resultTerm + standardOutValues).toTypedArray()
        return ResultArray(res)
    }
}
