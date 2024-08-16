package com.trufflegen.main

import com.oracle.truffle.api.frame.VirtualFrame

abstract class Computation : CBSNode() {
    abstract override fun execute(frame: VirtualFrame): Any
}

