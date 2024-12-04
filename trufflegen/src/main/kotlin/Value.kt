package main

import antlr.CBSParser

class Value(val value: CBSParser.ExprContext?) {
    val text: String? = value?.text
}
