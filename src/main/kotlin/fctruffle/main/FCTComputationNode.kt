package fctruffle.main

import com.oracle.truffle.api.frame.VirtualFrame

abstract class FCTComputationNode : FCTNode() {
    abstract override fun execute(frame: VirtualFrame): Any?
}

