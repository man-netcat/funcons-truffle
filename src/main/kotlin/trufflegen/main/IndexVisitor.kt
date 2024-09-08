package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class IndexVisitor : CBSBaseVisitor<Unit>() {
    internal val index = mutableListOf<String>()
    override fun visitIndexLine(line: IndexLineContext) {
        index.add(line.name.text)
    }
}
