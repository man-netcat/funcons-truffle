package fctruffle.parsetests

import fctruffle.main.FCTNode
import org.junit.jupiter.api.Assertions.assertTrue

open class BaseTest {
    fun performTest(node: FCTNode, expects: Any) {
        val rootNode = TestRootNode(node)
        val callTarget = rootNode.createCallTarget()
        val result = callTarget.call()
        assertTrue(result == expects)
    }
}
