package main.visitors

import cbs.CBSBaseVisitor
import cbs.CBSParser

class IndexVisitor : CBSBaseVisitor<Unit>() {
    val names: MutableSet<String> = mutableSetOf()

    override fun visitIndex(index: CBSParser.IndexContext) {
        names.addAll(index.indexLine().map { it.name.text })
    }
}