package interpreter

import com.oracle.truffle.api.frame.VirtualFrame

abstract class FCTComputationNode : FCTNode() {
    abstract override fun execute(frame: VirtualFrame): Any?


    /**
     * Retrieves a value from the scoped map within the given frame.
     */
    protected fun getInScope(frame: VirtualFrame, key: String): FCTEntity? {
        val scopedMap = frame.getObject(FCTFrameSlots.SCOPED_MAP_SLOT) as? MutableMap<String, FCTEntity>
        return scopedMap?.get(key) as? FCTEntity
    }

    /**
     * Adds or updates a value in the scoped map within the given frame.
     */
    protected fun putInScope(frame: VirtualFrame, key: String, value: FCTEntity) {
        val scopedMap = frame.getObject(FCTFrameSlots.SCOPED_MAP_SLOT) as? MutableMap<String, FCTEntity>
        if (scopedMap != null) {
            scopedMap[key] = value
        } else throw IllegalStateException("Scoped map not initialized in the frame.")
    }

    /**
     * Retrieves a value from the global map.
     */
    protected fun getGlobal(key: String): FCTEntity? {
        return FCTGlobalContext.globalMap[key] as? FCTEntity
    }

    /**
     * Adds or updates a value in the global map.
     */
    protected fun putGlobal(key: String, value: FCTEntity) {
        FCTGlobalContext.globalMap[key] = value
    }
}

