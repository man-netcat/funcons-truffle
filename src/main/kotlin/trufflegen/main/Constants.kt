package trufflegen.main

import fctruffle.main.FCTComputationNode
import fctruffle.main.FCTNode
import fctruffle.main.FCTTerminalNode

inline fun <reified T> clsName() = T::class.simpleName!!

val COMPUTATION = clsName<FCTComputationNode>()
val TERMINAL = clsName<FCTTerminalNode>()
val FCTNODE = clsName<FCTNode>()
