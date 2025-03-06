package language

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class FCTRootNode(
    language: FCTLanguage,
    frameDescriptor: FrameDescriptor,
    @Child private var rootExpr: FCTNode,
) : RootNode(language, frameDescriptor) {
    override fun execute(frame: VirtualFrame): Any {
        val result = rootExpr.execute(frame)
        return result.value
    }
}
