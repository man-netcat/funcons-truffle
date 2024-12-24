package interpreter

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

abstract class FCTNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any?

    fun getLanguage(): FCTLanguage {
        return FCTLanguage.get(this)
    }

    fun getContext(): FCTContext {
        return FCTContext.get(this)
    }
}
