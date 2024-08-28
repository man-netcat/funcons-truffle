package trufflegen.main

import trufflegen.antlr.CBSParser

class Value(value: CBSParser.ExprContext?) {
    val text: String? = value?.text
}
