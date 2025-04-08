package language

import builtin.SequenceNode
import builtin.TermNode
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import generated.StandardInEntity
import generated.StandardOutEntity

class FCTRootNode(
    language: FCTLanguage,
    frameDescriptor: FrameDescriptor,
    @Child private var rootTerm: TermNode,
    val inputNodes: Array<TermNode>,
) : RootNode(language, frameDescriptor) {
    override fun execute(frame: VirtualFrame): ResultArray {
        val standardInNode = StandardInEntity(SequenceNode(*inputNodes))
        rootTerm.appendGlobal("standard-in", standardInNode)
        val reduced = rootTerm.rewrite(frame)
        val resultTerm = listOf(reduced.value.toString())
        val standardOutNode = reduced.getGlobal("standard-out") as? StandardOutEntity
        val elements = standardOutNode?.p0?.elements.orEmpty()
        val standardOutValues = elements.map { it.value.toString() }

        val res = (resultTerm + standardOutValues).toTypedArray()
        return ResultArray(res)
    }
}
