package main.visitors

import cbs.CBSBaseVisitor
import cbs.CBSParser.IndexContext

class IndexVisitor : CBSBaseVisitor<Unit>() {
    val names: MutableSet<String> = mutableSetOf()

    override fun visitIndex(index: IndexContext) {
        names.addAll(index.indexLine().map { it.name.text })
    }
}