package interpreter

import com.oracle.truffle.api.frame.VirtualFrame

abstract class FCTComputationNode : FCTNode() {
    abstract override fun execute(frame: VirtualFrame): Any?

    protected fun getInScope(frame: VirtualFrame, key: String): FCTEntity? {
        val scopedMap = frame.getObject(0) as? MutableMap<String, FCTEntity>
        return scopedMap?.get(key)
    }

    protected fun putInScope(frame: VirtualFrame, value: FCTEntity) {
        val scopedMap = frame.getObject(0) as? MutableMap<String, FCTEntity>
        scopedMap?.put(value.name, value)
            ?: throw IllegalStateException("Scoped map not initialized in the frame.")
    }

    protected fun getGlobal(key: String): FCTEntity? {
        return getContext().getEntity(key) as? FCTEntity
    }

    protected fun putGlobal(key: String, value: FCTEntity) {
        getContext().putEntity(value)
    }
}
