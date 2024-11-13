package trufflegen.main

import trufflegen.antlr.CBSBaseVisitor
import trufflegen.antlr.CBSParser.*

class IndexVisitor : CBSBaseVisitor<Unit>() {
    val names: MutableSet<String> = mutableSetOf()

    override fun visitIndex(index: IndexContext) {
        names.addAll(index.indexLine().map { it.name.text })
    }
}
