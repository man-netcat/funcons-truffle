package language

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import generated.FalseNode
import generated.NullValueNode
import generated.TrueNode

class FCTRootNode(language: TruffleLanguage<*>, @Child private var rootExpr: FCTNode) : RootNode(language) {
    override fun execute(frame: VirtualFrame): Any? {
        val value = rootExpr.execute(frame)
        return when (value) {
            is TrueNode -> "true"
            is FalseNode -> "false"
            is NullValueNode -> "null-value"
            else -> throw IllegalStateException("Illegal execution value: ${value::class.simpleName}")
        }
    }
}
