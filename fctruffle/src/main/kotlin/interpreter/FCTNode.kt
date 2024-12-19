package interpreter

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

object FCTGlobalContext {
    val globalMap: MutableMap<String, Any> = mutableMapOf()
}

object FCTFrameSlots {
    const val SCOPED_MAP_SLOT = 0
}

abstract class FCTNode : Node() {
    abstract fun execute(frame: VirtualFrame): Any?

    fun getContext(): FCTContext {
        return FCTContext.get(this)
    }

    fun getLanguage(): FCTLanguage {
        return FCTLanguage.get(this)
    }
}
