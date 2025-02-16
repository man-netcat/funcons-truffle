package interpreter

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

@Suppress("UNCHECKED_CAST")
abstract class FCTNode : Node() {
    abstract val typeName: String?
    abstract fun execute(frame: VirtualFrame): Any?

    private fun getLanguage(): FCTLanguage {
        return FCTLanguage.get(this)
    }

    private fun getContext(): FCTContext {
        return FCTContext.get(this)
    }

    private fun getLocalContext(frame: VirtualFrame): MutableMap<String, FCTEntity?> {
        return frame.getObject(FrameSlots.LOCAL_CONTEXT.ordinal) as MutableMap<String, FCTEntity?>
    }

    protected fun getInScope(frame: VirtualFrame, key: String): FCTEntity? {
        return getLocalContext(frame)[key]
    }

    protected fun putInScope(frame: VirtualFrame, key: String, value: FCTEntity?) {
        getLocalContext(frame)[key] = value
    }

    protected fun isInScope(frame: VirtualFrame, key: String): Boolean {
        return key in getLocalContext(frame).keys
    }

    protected fun getGlobal(key: String): FCTEntity? {
        return getContext().getEntity(key)
    }

    protected fun putGlobal(key: String, value: FCTEntity) {
        getContext().putEntity(key, value)
    }

    protected fun isGlobal(key: String): Boolean {
        return key in getContext().entities.keys
    }
}
