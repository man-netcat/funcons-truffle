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
import java.nio.file.FileVisitOption
import java.nio.file.Files

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
    /*
      `thread-atomic` is `atomic` under the name the thread library uses. Its rules
      are written with `--->1 ; --->2`, which composes two transitions; the corpus
      says so itself -- "when the CBS notation `--->1 ; --->2` ... has been
      implemented by the interpreter generation tools, uses of `thread-atomic(X)`
      are to be replaced by `atomic(X)`". Generated, the composition became a rule
      whose body steps `thread-atomic(X')`, so reducing a body of any depth recursed
      once per step and every lock built on it ended in a stuck `and`.
     */
    "thread-atomic",                           // Atomic, under the thread library's name
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
 * @param renderFiles the base names of the source files to write node classes
 *   for, or null for every parsed file. All files are still parsed and
 *   collected, so names used in the rendered files resolve against the whole
 *   corpus even when only a part of it is emitted.
 * @param emitMetadata whether to write `Aliases.kt` and `NodeMetas.kt` too. A
 *   module that emits a subset keeps those in the module that owns the whole
 *   corpus: duplicating either file in a second module would redeclare its
 *   top-level symbols.
 */
class TruffleGen(
    private val cbsDir: File,
    private val outputDir: File = File("../fctlang/src/main/kotlin/generated"),
    private val renderFiles: Collection<String>? = null,
    private val emitMetadata: Boolean = true,
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

    /**
     * The `.cbs` files under [dir], walked top-down.
     *
     * Symlinked directories are followed. `Unstable-Funcons-beta` contains a
     * symlink back to the stable `Funcons-beta`, and the unstable rules name
     * funcons declared there (`give`, `checked`), so the registry only resolves
     * them if that tree is walked too. `File.walkTopDown` does not descend into
     * symlinks and would yield 8 files where there are 51.
     */
    private fun cbsFiles(dir: File): List<File> =
        Files.walk(dir.toPath(), FileVisitOption.FOLLOW_LINKS)
            .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".cbs") }
            .map { path -> path.toFile() }
            .toList()

    private fun parseCorpus(dir: File): List<SourceFile> =
        cbsFiles(dir)
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
        val generated = corpus.byFile
            .filterKeys { renderFiles == null || it in renderFiles }
            .mapNotNull { (name, objects) ->
            println("Generating code for file $name...")
            val code = renderFile(name, objects, included) ?: return@mapNotNull null
            GeneratedFile(name.removeSuffix(".cbs") + ".kt", code)
        }

        val metadata = if (emitMetadata) listOf(
            GeneratedFile("Aliases.kt", renderAliases(corpus.registry, included)),
            GeneratedFile("NodeMetas.kt", renderNodeMetas(corpus.registry, included)),
        ) else emptyList()

        return generated + metadata
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
                println("Usage: trufflegen <cbsDir> [outputDir] [--render=base.kt,...] [--no-metadata]")
                return
            }

            val cbsDir = File(args[0])
            val outputDir = args.getOrNull(1)?.takeIf { !it.startsWith("--") }
                ?: "../fctlang/src/main/kotlin/generated"
            val renderFiles = args.getOrNull(1)?.takeIf { it.startsWith("--render=") }
                ?.removePrefix("--render=")?.split(",")
            val renderFilesAlt = args.getOrNull(2)?.takeIf { it.startsWith("--render=") }
                ?.removePrefix("--render=")?.split(",")
            val emitMetadata = !args.any { it == "--no-metadata" }

            val truffleGen = TruffleGen(
                cbsDir,
                File(outputDir),
                renderFiles ?: renderFilesAlt,
                emitMetadata,
            )

            truffleGen.process()
        }
    }
}
