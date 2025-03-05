package language

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class FCTRootNode(language: TruffleLanguage<*>, @Child private var rootExpr: FCTNode) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        val result = rootExpr.execute(frame)
        return result.value
    }
}
