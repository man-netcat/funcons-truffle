package language

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class FCTRootNode(language: TruffleLanguage<*>?, private var rootExpr: FCTNode) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any? {
        val mapInit: MutableMap<String, Entity> = mutableMapOf()
        frame.setObject(FrameSlots.LOCAL_CONTEXT.ordinal, mapInit)
        return rootExpr.execute(frame)
    }
}