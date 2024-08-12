package com.trufflegen.stc

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

class CBSRootNode(private val rootNode: CBSNode) : RootNode(null) {
    override fun execute(frame: VirtualFrame): Any {
        return rootNode.execute(frame)
    }
}

fun callNode(node: CBSNode): Any? {
    val rootNode: RootNode = CBSRootNode(node)
    val callTarget: CallTarget = Truffle.getRuntime().createCallTarget(rootNode)

    return callTarget.call()
}