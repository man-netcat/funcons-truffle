package language

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import generated.StandardInNode
import generated.StandardOutNode
import language.Util.DEBUG

class FCTRootNode(
    language: FCTLanguage,
    frameDescriptor: FrameDescriptor,
    @Child private var rootTerm: TermNode,
    val inputNodes: Array<TermNode>,
) : RootNode(language, frameDescriptor) {
    override fun execute(frame: VirtualFrame): ResultArray {
        val standardIn = StandardInNode(SequenceNode(*inputNodes))
        rootTerm.appendGlobal("standard-in", standardIn)

        var iterationCount = 0
        while (rootTerm.isReducible()) {
            if (DEBUG) {
                println("------------------")
                println("Iteration $iterationCount: Current result is ${rootTerm::class.simpleName}")
                rootTerm.printTree()
            }
//            println(rootExpr.getContext().globalVariables)
            rootTerm = rootTerm.reduce(frame)
            iterationCount++
        }

        val resultTerm = listOf(rootTerm.value.toString())
        val stdOut =
            (rootTerm.getGlobal("standard-out") as StandardOutNode?)?.p0?.elements
                ?.map { it.value.toString() } ?: emptyList()

        val res = (resultTerm + stdOut).toTypedArray()

        return ResultArray(res)
    }
}
