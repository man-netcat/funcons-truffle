package fctruffle.main

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class FCTRootNode(language: TruffleLanguage<*>?, private var rootExpr: FCTNode) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        return rootExpr.execute(frame)
    }
}