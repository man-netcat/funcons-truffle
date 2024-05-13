package com.trufflegen.main

import com.trufflegen.generated.*
import com.oracle.truffle.api.frame.VirtualFrame

fun main(args: Array<String>) {
    val FALSE = BooleansNode("false")
    val TRUE = BooleansNode("true")
    val frame = VirtualFrame.createFrame()
    val n = AndNode(TRUE, TRUE, TRUE, FALSE)
    println(n.execute(frame))
}