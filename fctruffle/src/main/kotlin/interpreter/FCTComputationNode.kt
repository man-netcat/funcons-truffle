package interpreter

import com.oracle.truffle.api.frame.VirtualFrame

@Suppress("UNCHECKED_CAST")
abstract class FCTComputationNode : FCTNode() {
    abstract override fun execute(frame: VirtualFrame): Any?

    protected fun getInScope(frame: VirtualFrame, key: String): FCTEntity? {
        val localContext = frame.getObject(FrameSlots.LOCAL_CONTEXT.ordinal) as MutableMap<String, FCTEntity>
        return localContext[key]
    }

    protected fun putInScope(frame: VirtualFrame, value: FCTEntity) {
        val localContext = frame.getObject(FrameSlots.LOCAL_CONTEXT.ordinal) as MutableMap<String, FCTEntity>
        localContext.put(value.name, value) ?: throw IllegalStateException("Scoped map not initialized in the frame.")
    }

    protected fun getGlobal(key: String): FCTEntity? {
        return getContext().getEntity(key)
    }

    protected fun putGlobal(key: String, value: FCTEntity): Boolean {
        return getContext().putEntity(key, value)
    }
}