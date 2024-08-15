package com.trufflegen.parsetests

import com.trufflegen.stc.CBSNode
import com.trufflegen.stc.callNode
import org.junit.jupiter.api.Assertions.assertTrue

open class BaseTest {
    fun performTest(node: CBSNode, expects: Any) {
        val result = callNode(node)
        assertTrue(result == expects)
    }
}
