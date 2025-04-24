package main

import cbs.CBSLexer
import cbs.CBSParser
import fct.FCTLexer
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
import java.nio.file.Files
import java.nio.file.Path

val globalObjects: MutableMap<String, Object> = mutableMapOf()
val globalFiles: MutableMap<String, File> = mutableMapOf()
val builtinOverride: MutableSet<String> = mutableSetOf(
    "left-to-right", "right-to-left",          // Ambiguous semantics
    "some-element", "choice", "else-choice",   // Utilises random
    "sequential",                              // Param after sequence
    "stuck", "abstraction",                    // No rules, implement manually
    "read", "print",                           // Input/Output
    "hole", "resume-continuation",             // Read-only control entity
    "identifiers", "pointers", "pointer-null", // Weird Set Syntax
    "atomic",                                  // Atomic
    "structural-assign", "structural-assigned", "match", "match-loosely", // Depends on tuple unpacking
)

class TruffleGen(
    private val cbsDir: File,
    private val outputDir: File,
    private vararg val fctFiles: File,
    private val indexFile: File?,
) {
    private val cbsFiles: MutableMap<String, CBSFile> = mutableMapOf()
    private var cbsParseTrees = mutableMapOf<String, CBSParser.RootContext>()
    private val fctParseTrees = mutableMapOf<String, FCTParser.RootContext>()
    private var indexParseTree: CBSParser.RootContext? = null
    private val generatedDependencies = mutableSetOf<Object>()

    fun process() {
        // First generate the parse trees for all .cbs files
        generateParseTrees()

        // Generate classes that hold data for all funcons, datatypes, entities, etc...
        generateObjects()

        // Verify all objects are accounted for
        verifyObjects()

        // Generate dependencies from objects
        generateDependencies()

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

        fctFiles.forEach { file ->
            val input = CharStreams.fromPath(file.toPath())
            val lexer = FCTLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = FCTParser(tokens)
            val root = parser.root()
            fctParseTrees[file.name] = root
        }

        indexFile?.let { file ->
            val input = CharStreams.fromPath(file.toPath())
            val lexer = CBSLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = CBSParser(tokens)
            val root = parser.root()
            indexParseTree = root
        }
    }

    private fun generateObjects() {
        cbsParseTrees.forEach { (name, root) ->
            println("Generating objects for file $name...")
            val cbsFile = CBSFile(name)
            cbsFile.visit(root)
            val fileObjects = cbsFile.objects
            if (fileObjects.isNotEmpty()) {
                cbsFiles[name] = cbsFile
                globalObjects.putAll(fileObjects)
            }
        }
    }

    private fun verifyObjects() {
        cbsParseTrees.forEach { (name, root) ->
            println("Verifying all objects accounted for for file $name...")
            val visitor = IndexVisitor()
            visitor.visit(root)
            visitor.names.forEach {
                if (it !in globalObjects.keys) {
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

        cbsFiles.values.forEach { file ->
            file.objects.values.distinct().forEach { obj ->
                println("Generating object dependencies for object ${obj.name}...")
                fun visitDependencies(obj: Object) {
                    val visitor = CBSDependencyVisitor()
                    visitor.visit(obj.ctx)
                    val objDependencies = visitor.dependencies
                        .map { objName -> globalObjects[objName]!! }
                        .filter { dep -> dep.name != obj.name }
                        .distinct()
                    obj.dependencies.addAll(objDependencies)
                }

                when (obj) {
                    is TypeObject -> {
                        obj.dependencies.add(globalObjects["value-types"]!!)
                        visitDependencies(obj)
                    }

                    is AlgebraicDatatypeObject -> {
                        obj.dependencies.add(globalObjects["datatype-values"]!!)
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

        fun visitObject(obj: Object) {
            if (obj !in generatedDependencies) {
                generatedDependencies.add(obj)
                obj.dependencies.forEach { visitObject(it) }
            }
        }

        usedFuncons.forEach { name ->
            globalObjects[name]?.let { visitObject(it) }
        }
    }

    private fun generateCode() {
        val outputDirPath = Path.of(outputDir.path)
        if (!Files.exists(outputDirPath)) {
            Files.createDirectories(outputDirPath)
        }

        cbsFiles.forEach { (name, file) ->
            println("Generating code for file $name...")
            val code = file.generateCode(generatedDependencies)
            if (code != null) {
                val fileNameWithoutExtension = name.removeSuffix(".cbs")
                val filePath = File(outputDir, "$fileNameWithoutExtension.kt")
                filePath.writeText(code)
            }
        }
        val aliasFilePath = File(outputDir, "Aliases.kt")
        val stringBuilder = StringBuilder()

        stringBuilder.appendLine("package generated")
        stringBuilder.appendLine("import language.*")
        stringBuilder.appendLine("import builtin.*")
        stringBuilder.appendLine()

        stringBuilder.appendLine("val aliasMap: Map<String, String> = mapOf(")
        val objectsToProcess = if (generatedDependencies.isNotEmpty()) {
            globalObjects
                .values.distinct()
                .filter { obj -> obj in generatedDependencies && obj !is EntityObject }
        } else globalObjects.values.toSet().filter { obj -> obj !is EntityObject }

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

        aliasFilePath.writeText(stringBuilder.toString())
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 2) {
                println("Usage: <program> <cbsDir> <outputDir> [--index <indexFile>] [included...]")
                return
            }

            val cbsDir = File(args[0])
            val outputDir = File(args[1])
            val fctFiles = mutableListOf<File>()
            var indexFile: File? = null

            var i = 2
            while (i < args.size) {
                when (args[i]) {
                    "--index" -> {
                        if (i + 1 < args.size) {
                            indexFile = File(args[i + 1])
                            i++
                        } else {
                            println("Error: --index option requires a file argument.")
                            return
                        }
                    }

                    else -> fctFiles.add(File(args[i]))
                }
                i++
            }

            listOf(cbsDir, outputDir).map { file ->
                if (!file.exists() || !file.isDirectory) {
                    println("Invalid directory: ${file.path}")
                    return
                }
            }

            fctFiles.map { file ->
                if (!file.exists() || !file.isFile) {
                    println("Invalid file: ${file.path}")
                    return
                }
            }

            val truffleGen = TruffleGen(cbsDir, outputDir, *fctFiles.toTypedArray(), indexFile = indexFile)

            truffleGen.process()
        }
    }
}
