package language

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import generated.ValuesNode

class FCTRootNode(
    language: FCTLanguage,
    frameDescriptor: FrameDescriptor,
    @Child private var rootExpr: TermNode,
) : RootNode(language, frameDescriptor) {
    override fun execute(frame: VirtualFrame): Any {
        var iterationCount = 0

        while (rootExpr !is ValuesNode) {
//            println("------------------")
//            println("Iteration $iterationCount: Current result is ${rootExpr::class.simpleName}")
//            rootExpr.printTree()
//            println(rootExpr.getContext().globalVariables)
            rootExpr = rootExpr.execute(frame)
            iterationCount++
        }

        return rootExpr.value
    }
}
