package interpreter

import com.oracle.truffle.api.TruffleLanguage.ContextReference
import com.oracle.truffle.api.TruffleLanguage.Env
import com.oracle.truffle.api.nodes.Node

class FCTContext(env: Env) {
    val entities: MutableMap<String, FCTEntity> = mutableMapOf()

    companion object {
        private val REFERENCE: ContextReference<FCTContext> = ContextReference.create(FCTLanguage::class.java)

        fun get(node: Node): FCTContext {
            return REFERENCE.get(node)
        }
    }

    fun putEntity(key: String, entity: FCTEntity): Boolean {
        val existingValue = entities.put(key, entity)
        if (existingValue != null && existingValue !is FCTMutableEntity) {
            throw IllegalStateException("Trying to modify immutable entity")
        }
        return existingValue == null
    }

    fun getEntity(name: String): FCTEntity? {
        return entities[name]
    }
}
