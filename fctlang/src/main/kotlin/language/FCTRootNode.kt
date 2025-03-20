package language

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import generated.StandardOutNode

class FCTRootNode(
    language: FCTLanguage,
    frameDescriptor: FrameDescriptor,
    @Child private var rootExpr: TermNode,
) : RootNode(language, frameDescriptor) {
    override fun execute(frame: VirtualFrame): ResultArray {
        var iterationCount = 0

        while (rootExpr !is ValuesNode) {
//            println("------------------")
//            println("Iteration $iterationCount: Current result is ${rootExpr::class.simpleName}")
//            rootExpr.printTree()
//            println(rootExpr.getContext().globalVariables)
            rootExpr = rootExpr.reduce(frame)
            iterationCount++
        }

        val resultTerm = listOf(rootExpr.value.toString())
        val stdOut =
            (rootExpr.getGlobal("standard-out") as StandardOutNode?)?.p0?.elements
                ?.map { it.value.toString() } ?: emptyList()

        val res = (resultTerm + stdOut).toTypedArray()

        return ResultArray(res)
    }
}
