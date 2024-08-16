package com.trufflegen.main

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class CBSRootNode(language: TruffleLanguage<*>?, private var rootExpr: CBSNode) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any {
        return rootExpr.execute(frame)
    }
}