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
    private val expectations: TestExpectations,
    private val refocus: Boolean = true
) : RootNode(language, frameDescriptor) {
    override fun execute(frame: VirtualFrame): ResultArray {
        frame.setBoolean(FCTLanguage.REFOCUS_SLOT, refocus)
        val standardInNode = SequenceNode(*inputNodes)
        appendEntity(frame, "standard-in", standardInNode)
        val resultTerm = rootTerm.rewrite(frame)
        val resultValue = resultTerm.toString()
        val storeValue = getEntity(frame, "store").toString()
        val standardOutValues = getEntity(frame, "standard-out").elements.map { it.toString() }

        val comparison = ExpectationCheck.compare(
            expectations = expectations,
            actualResultTerm = resultValue,
            actualStore = storeValue,
            actualStandardOut = standardOutValues
        )

        val res = (listOf(resultValue) + listOf(storeValue) + standardOutValues).toTypedArray()
        return ResultArray(res, expectations, comparison)
    }
}
