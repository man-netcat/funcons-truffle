package trufflegen.main

import trufflegen.antlr.CBSParser

class Value(value: CBSParser.ExprContext?) {
    val name: String? = value?.text
}
