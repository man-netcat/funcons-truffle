package language

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.TruffleLanguage.ContextReference

class FCTContext(env: TruffleLanguage.Env) {
    internal val globalVariables = GlobalScope()

    companion object {
        val REF: ContextReference<FCTContext> = ContextReference.create(FCTLanguage::class.java)
        fun get(node: TermNode?): FCTContext? = REF.get(node)
    }
}