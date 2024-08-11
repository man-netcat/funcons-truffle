package com.trufflegen.main

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import com.trufflegen.generated.AndNode
import com.trufflegen.generated.BooleansNode
import com.trufflegen.stc.CBSNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertInstanceOf

class CBSRootNode(private val rootNode: CBSNode) : RootNode(null) {
    override fun execute(frame: VirtualFrame): Any {
        return rootNode.execute(frame)
    }
}

fun main() {
    val falseNode = BooleansNode("false")
    val trueNode = BooleansNode("true")
    val andNode = AndNode(trueNode, trueNode, trueNode, falseNode)

    val rootNode: RootNode = CBSRootNode(andNode)
    val callTarget: CallTarget = Truffle.getRuntime().createCallTarget(rootNode)

    try {
        val result = callTarget.call()
        println("Result: $result")
        assertInstanceOf<BooleansNode>(result)
        assertEquals(falseNode, result)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
