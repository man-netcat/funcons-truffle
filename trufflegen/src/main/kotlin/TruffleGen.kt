package main

import cbs.CBSLexer
import cbs.CBSParser
import fct.FCTParser
import main.exceptions.DetailedException
import main.objects.AlgebraicDatatypeObject
import main.objects.EntityObject
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

class TruffleGen(
    private val cbsDir: File,
    private val outputDir: File = File("../fctlang/src/main/kotlin/generated"),
) {
    private val registry = Registry(builtinOverride)
    private val fileObjects: MutableMap<String, MutableSet<Object>> = mutableMapOf()
    private var cbsParseTrees = mutableMapOf<String, CBSParser.RootContext>()
    private val fctParseTrees = mutableMapOf<String, FCTParser.RootContext>()
    private var indexParseTree: CBSParser.RootContext? = null

    /**
     * The objects reachable from the entry points, or null while the dependency
     * pass is off.
     *
     * `generateDependencies` is not called, so this stays null and every object is
     * written. It is null rather than an empty set because those are different
     * requests: with the pass on and nothing reachable, no code should be written
     * at all, which an empty set that also meant "everything" could not express.
     */
    private var generatedDependencies: Set<Object>? = null

    fun process() {
        // First generate the parse trees for all .cbs files
        generateParseTrees()

        // Generate classes that hold data for all funcons, datatypes, entities, etc...
        generateObjects()

        // Verify all objects are accounted for
        verifyObjects()

        // Generate dependencies from objects
//        generateDependencies()

        // Generates a graphviz visualisation for dependencies
//        generateGraphViz()

        // Generate code from the objects
        generateCode()
    }

    private fun generateParseTrees() {
        cbsDir.walkTopDown().filter { file -> file.isFile && file.extension == "cbs" }.forEach { file ->
            val input = CharStreams.fromPath(file.toPath())
            val lexer = CBSLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = CBSParser(tokens)
            val root = parser.root()
            cbsParseTrees[file.name] = root
        }
    }

    private fun generateObjects() {
        cbsParseTrees.forEach { (name, root) ->
            println("Generating objects for file $name...")
            val collector = ObjectCollector(registry)
            collector.collect(root)
            if (collector.objects.isNotEmpty()) {
                fileObjects[name] = collector.objects
            }
        }
    }

    private fun verifyObjects() {
        cbsParseTrees.forEach { (name, root) ->
            println("Verifying all objects accounted for for file $name...")
            val visitor = IndexVisitor()
            visitor.visit(root)
            visitor.names.forEach {
                if (registry[it] == null) {
                    throw DetailedException("$it does not have an object")
                }
            }
        }
    }

    private fun generateDependencies() {
        val usedFuncons = if (indexParseTree != null) {
            val indexVisitor = IndexVisitor()
            indexVisitor.visit(indexParseTree)
            indexVisitor.names.distinct()
        } else {
            fctParseTrees.flatMap { (name, root) ->
                val fctVisitor = FCTDependencyVisitor()
                fctVisitor.visit(root)
                fctVisitor.dependencies
            }.distinct()
        }

        fileObjects.values.forEach { objects ->
            objects.forEach { obj ->
                println("Generating object dependencies for object ${obj.name}...")
                fun visitDependencies(obj: Object) {
                    val visitor = CBSDependencyVisitor()
                    visitor.visit(obj.ctx)
                    val objDependencies = visitor.dependencies
                        .map { objName -> registry.require(objName) }
                        .filter { dep -> dep.name != obj.name }
                        .distinct()
                    obj.dependencies.addAll(objDependencies)
                }

                when (obj) {
                    is TypeObject -> {
                        obj.dependencies.add(registry.require("value-types"))
                        visitDependencies(obj)
                    }

                    is AlgebraicDatatypeObject -> {
                        obj.dependencies.add(registry.require("datatype-values"))
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
            registry[name]?.let { visitObject(it) }
        }

        generatedDependencies = reachable
    }

    private fun generateCode() {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // Generate CBS files
        fileObjects.forEach { (name, objects) ->
            println("Generating code for file $name...")
            val code = renderFile(name, objects, generatedDependencies)
            if (code != null) {
                val fileNameWithoutExtension = name.removeSuffix(".cbs")
                val filePath = File(outputDir, "$fileNameWithoutExtension.kt")
                filePath.bufferedWriter().use { writer ->
                    writer.write(code)
                    writer.flush()
                }
            }
        }

        // Generate alias file
        val aliasFilePath = File(outputDir, "Aliases.kt")
        val stringBuilder = StringBuilder()

        stringBuilder.appendLine("package generated")
        stringBuilder.appendLine("import language.*")
        stringBuilder.appendLine("import builtin.*")
        stringBuilder.appendLine()

        stringBuilder.appendLine("val aliasMap: Map<String, String> = mapOf(")
        val included = generatedDependencies
        val objectsToProcess = if (included != null) {
            registry.objects.filter { obj -> obj in included && obj !is EntityObject }
        } else registry.objects.filter { obj -> obj !is EntityObject }

        objectsToProcess.flatMap { obj ->
            obj.aliases
                .asSequence()
                .filterNot { alias -> alias == obj.name }
                .map { alias -> "\"$alias\" to \"${obj.name}\"" }
        }
            .joinToString(",\n    ", prefix = "    ", postfix = "\n)")
            .let(stringBuilder::append)

        stringBuilder.appendLine()
        objectsToProcess.flatMap { obj ->
            obj.aliases
                .asSequence()
                .filterNot { alias -> alias == obj.name }
                .map { alias ->
                    makeTypeAlias(toNodeName(alias), obj.nodeName)
                }
        }
            .joinToString("\n")
            .let(stringBuilder::appendLine)

        aliasFilePath.bufferedWriter().use { writer ->
            writer.write(stringBuilder.toString())
            writer.flush()
        }
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
