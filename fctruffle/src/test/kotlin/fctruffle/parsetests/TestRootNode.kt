package fctruffle.parsetests

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import fctruffle.main.CBSNode

class TestRootNode(private val rootNode: CBSNode) : RootNode(null) {

    override fun execute(frame: VirtualFrame): Any {
        return rootNode.execute(frame)
    }

    fun createCallTarget(): CallTarget {
        return Truffle.getRuntime().createCallTarget(this)
    }
}
