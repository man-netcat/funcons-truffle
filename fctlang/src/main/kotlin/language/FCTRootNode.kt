package language

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class FCTRootNode(language: TruffleLanguage<*>, @Child private var rootExpr: FCTNode) : RootNode(language) {
    private val frameDescriptor: FrameDescriptor = FrameDescriptor.newBuilder(10).build()

    override fun execute(frame: VirtualFrame): Any? {
//        val mapSlot = frameDescriptor.findOrAddAuxiliarySlot("context")
//
//        frame.setObject(mapSlot, mutableMapOf<String, Entity>())

        rootExpr.execute(frame)
        return null
    }
}
