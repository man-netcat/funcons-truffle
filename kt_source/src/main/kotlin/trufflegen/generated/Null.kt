package trufflegen.generated

import trufflegen.main.*
import trufflegen.main.Util.Companion.slice
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.NodeInfo

class NullTypeNode(override val value: String) : Terminal() {
    override fun execute(frame: VirtualFrame): String = value
}