package main

import cbs.CBSLexer
import cbs.CBSParser
import fct.FCTParser
import main.exceptions.DetailedException
import main.objects.AlgebraicDatatypeObject
import main.objects.Object
import main.objects.TypeObject
import main.visitors.CBSDependencyVisitor
import main.visitors.FCTDependencyVisitor
import main.visitors.IndexVisitor
import objects.DatatypeFunconObject
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File

/**
 * Names the corpus declares but Truffle implements differently, so no node class
 * is generated for them.
 *
 * They stay in the registry: a rule that names one still resolves, it is only the
 * generated class that is skipped.
 */
private val builtinOverride = setOf(
    "left-to-right", "right-to-left",          // Ambiguous semantics
    "some-element", "choice", "else-choice",   // Implemented leftmost, not randomly
    "sequential",                              // Param after sequence
    "stuck", "abstraction",                    // No rules, implement manually
    "read", "print",                           // Input/Output
    "hole", "resume-continuation",             // Read-only control entity
    "atomic",                                  // Atomic
    "strings",                                 // Also using literal strings for datatype ids
    "tuple-elements",                          // Must be unpacked manually
)

/** One `.cbs` file of the corpus, parsed but not yet read as definitions. */
private data class SourceFile(val name: String, val root: CBSParser.RootContext)

/**
 * The corpus after collection: the objects, and which file declared them.
 *
 * @param registry every object, by name and by alias. Read-only from here on:
 * collection is the only stage that adds to it.
 * @param byFile the objects each file declared, in declaration order.
 */
private class Corpus(val registry: Registry, val byFile: Map<String, Set<Object>>)

/** One file of generated Kotlin, ready to write. */
private data class GeneratedFile(val fileName: String, val content: String)

/**
 * Generates the funcon node classes for a corpus, as a sequence of stages over
 * explicit data: parse the files, collect their objects, verify every declared
 * name resolves, select what to write, render the Kotlin, write it.
 *
 * @param cbsDir the directory of `.cbs` files to read, walked top-down.
 * @param outputDir where the generated Kotlin is written.
 */
class TruffleGen(
    private val cbsDir: File,
    private val outputDir: File = File("../fctlang/src/main/kotlin/generated"),
) {
    // The dependency pass below reads these. Nothing fills them: the pass is not
    // wired up, and until it is, they stay empty and null.
    private val fctParseTrees = mutableMapOf<String, FCTParser.RootContext>()
    private var indexParseTree: CBSParser.RootContext? = null

    fun process() {
        val sources = parseCorpus(cbsDir)
        val corpus = collectCorpus(sources)
        verifyCorpus(sources, corpus)
        val included = selectObjects(corpus)
        val generated = renderCorpus(corpus, included)
        writeAll(outputDir, generated)
    }

    private fun parseCorpus(dir: File): List<SourceFile> =
        dir.walkTopDown()
            .filter { file -> file.isFile && file.extension == "cbs" }
            .map { file ->
                val input = CharStreams.fromPath(file.toPath())
                val parser = CBSParser(CommonTokenStream(CBSLexer(input)))
                SourceFile(file.name, parser.root())
            }
            .toList()

    private fun collectCorpus(sources: List<SourceFile>): Corpus {
        val builder = RegistryBuilder(builtinOverride)
        val byFile = LinkedHashMap<String, Set<Object>>()

        sources.forEach { source ->
            println("Generating objects for file ${source.name}...")
            val collector = ObjectCollector(builder)
            collector.collect(source.root)
            if (collector.objects.isNotEmpty()) {
                byFile[source.name] = collector.objects
            }
        }

        return Corpus(builder, byFile)
    }

    private fun verifyCorpus(sources: List<SourceFile>, corpus: Corpus) {
        sources.forEach { source ->
            println("Verifying all objects accounted for for file ${source.name}...")
            val visitor = IndexVisitor()
            visitor.visit(source.root)
            visitor.names.forEach { name ->
                if (corpus.registry[name] == null) {
                    throw DetailedException("$name does not have an object")
                }
            }
        }
    }

    /**
     * The objects to generate code for, or null for all of them.
     *
     * The dependency pass that would compute a subset ([generateDependencies]) is
     * retained below but not called: nothing fills `fctParseTrees` or
     * `indexParseTree`, so it would reach nothing. Until it is wired up every
     * object is selected — and this returns null rather than an empty set so that
     * "select everything" cannot be confused with "select nothing".
     */
    @Suppress("UNUSED_PARAMETER")
    private fun selectObjects(corpus: Corpus): Set<Object>? = null

    private fun renderCorpus(corpus: Corpus, included: Set<Object>?): List<GeneratedFile> {
        val generated = corpus.byFile.mapNotNull { (name, objects) ->
            println("Generating code for file $name...")
            val code = renderFile(name, objects, included) ?: return@mapNotNull null
            GeneratedFile(name.removeSuffix(".cbs") + ".kt", code)
        }

        return generated + GeneratedFile("Aliases.kt", renderAliases(corpus.registry, included))
    }

    private fun writeAll(dir: File, generated: List<GeneratedFile>) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        generated.forEach { file ->
            File(dir, file.fileName).bufferedWriter().use { writer -> writer.write(file.content) }
        }
    }

    /**
     * The objects reachable from the entry points, for [selectObjects] to use.
     *
     * Retained in full though it is not called: it is the only description of the
     * dependency pass, and turning it off should not mean deleting it. It reads
     * `fctParseTrees` and `indexParseTree`, which nothing populates yet, so wiring
     * it up means filling those first.
     */
    private fun generateDependencies(corpus: Corpus): Set<Object> {
        val usedFuncons = if (indexParseTree != null) {
            val indexVisitor = IndexVisitor()
            indexVisitor.visit(indexParseTree)
            indexVisitor.names.distinct()
        } else {
            fctParseTrees.flatMap { (_, root) ->
                val fctVisitor = FCTDependencyVisitor()
                fctVisitor.visit(root)
                fctVisitor.dependencies
            }.distinct()
        }

        corpus.byFile.values.forEach { objects ->
            objects.forEach { obj ->
                println("Generating object dependencies for object ${obj.name}...")
                fun visitDependencies(obj: Object) {
                    val visitor = CBSDependencyVisitor()
                    visitor.visit(obj.ctx)
                    val objDependencies = visitor.dependencies
                        .map { objName -> corpus.registry.require(objName) }
                        .filter { dep -> dep.name != obj.name }
                        .distinct()
                    obj.dependencies.addAll(objDependencies)
                }

                when (obj) {
                    is TypeObject -> {
                        obj.dependencies.add(corpus.registry.require("value-types"))
                        visitDependencies(obj)
                    }

                    is AlgebraicDatatypeObject -> {
                        obj.dependencies.add(corpus.registry.require("datatype-values"))
                        obj.definitions.forEach { dep -> obj.dependencies.add(dep) }
                    }

                    is DatatypeFunconObject -> {
                        obj.dependencies.add(obj.superclass)
                        visitDependencies(obj.superclass)
                    }

                    else -> visitDependencies(obj)
                }
            }
        }

        val reachable = mutableSetOf<Object>()

        fun visitObject(obj: Object) {
            if (obj !in reachable) {
                reachable.add(obj)
                obj.dependencies.forEach { visitObject(it) }
            }
        }

        usedFuncons.forEach { name ->
            corpus.registry[name]?.let { visitObject(it) }
        }

        return reachable
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: trufflegen <cbsDir>")
                return
            }

            val cbsDir = File(args[0])

            val truffleGen = TruffleGen(cbsDir)

            truffleGen.process()
        }
    }
}
