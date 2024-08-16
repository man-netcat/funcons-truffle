package com.trufflegen.parsetests

import com.trufflegen.main.CBSNode
import org.junit.jupiter.api.Assertions.assertTrue

open class BaseTest {
    fun performTest(node: CBSNode, expects: Any) {
        val rootNode = TestRootNode(node)
        val callTarget = rootNode.createCallTarget()
        val result = callTarget.call()
        assertTrue(result == expects)
    }
}
