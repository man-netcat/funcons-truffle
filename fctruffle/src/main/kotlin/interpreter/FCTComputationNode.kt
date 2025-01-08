package interpreter

import com.oracle.truffle.api.frame.VirtualFrame

abstract class FCTComputationNode : FCTNode() {
    abstract override fun execute(frame: VirtualFrame): FCTNode?

    protected fun getInScope(frame: VirtualFrame, key: String): FCTEntity? {
        val localContext = frame.getObject(FrameSlots.LOCAL_CONTEXT)
        return localContext?.get(key)
    }

    protected fun putInScope(frame: VirtualFrame, value: FCTEntity) {
        val localContext = frame.getObject(FrameSlots.LOCAL_CONTEXT)
        localContext?.put(value.name, value) ?: throw IllegalStateException("Scoped map not initialized in the frame.")
    }

    protected fun getGlobal(key: String): FCTEntity? {
        return getContext().getEntity(key)
    }

    protected fun putGlobal(key: String, value: FCTEntity): Boolean {
        return getContext().putEntity(key, value)
    }
}

fun VirtualFrame.getObject(slot: FrameSlots): MutableMap<String, FCTEntity>? {
    return getObject(slot.ordinal) as? MutableMap<String, FCTEntity>
}