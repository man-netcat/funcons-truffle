package main.visitors

import antlr.CBSBaseVisitor
import antlr.CBSParser

class IndexVisitor : CBSBaseVisitor<Unit>() {
    val names: MutableSet<String> = mutableSetOf()

    override fun visitIndex(index: CBSParser.IndexContext) {
        names.addAll(index.indexLine().map { it.name.text })
    }
}