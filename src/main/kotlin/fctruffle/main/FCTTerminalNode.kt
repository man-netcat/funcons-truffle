package fctruffle.main

import com.oracle.truffle.api.frame.VirtualFrame

abstract class FCTTerminalNode() : FCTNode() {
    override fun execute(frame: VirtualFrame): Any? {
        return TODO("Provide the return value")
    }
}