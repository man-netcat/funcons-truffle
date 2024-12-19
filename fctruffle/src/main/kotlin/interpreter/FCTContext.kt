package interpreter

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.Env

class FCTContext(val env: Env) {
    val globalMap: MutableMap<String, Any> = mutableMapOf()

    companion object {
        private val reference: TruffleLanguage.ContextReference<FCTContext> =
            TruffleLanguage.ContextReference.create(FCTLanguage::class.java)

        fun get(node: FCTNode): FCTContext {
            return reference.get(node)
        }
    }
}