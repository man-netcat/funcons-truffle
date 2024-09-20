package trufflegen.main

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import trufflegen.antlr.CBSLexer
import trufflegen.antlr.CBSParser
import trufflegen.antlr.CBSParser.RootContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

private val globalObjects: MutableMap<String, Object> = mutableMapOf()

class TruffleGen(private val cbsDir: File, private val languageIndex: File?) {
    private val files: MutableMap<String, CBSFile> = mutableMapOf()
    private var index: MutableSet<String> = mutableSetOf<String>()
    private var dependencies: MutableSet<String> = mutableSetOf<String>()
    private var builtins: MutableSet<String> = mutableSetOf<String>()
    private var parseTrees: MutableMap<String, RootContext> = mutableMapOf<String, RootContext>()

    fun process() {
        buildIndex()

        generateParseTrees()
        findDependencies()

        index.removeAll(builtins)
        index.forEach { println("Index: $it") }
        dependencies.forEach { println("Dependency: $it") }
        builtins.forEach { println("Builtin: $it") }

        if (languageIndex != null) {
            println("Index: ${index.size}, Dependencies: ${dependencies.size}, Total: ${index.size + dependencies.size} (Builtins: ${builtins.size})")
        }
        index.addAll(dependencies)

        generateObjects()
        generateCode()
    }


    fun buildIndex() {
        if (languageIndex == null) return
        val input = CharStreams.fromPath(languageIndex.toPath())
        val lexer = CBSLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = CBSParser(tokens)
        val root = parser.root()
        val indexVisitor = IndexVisitor()
        indexVisitor.visit(root)
        index = indexVisitor.index
    }

    private fun generateParseTrees() {
        cbsDir.walkTopDown().filter { file -> file.isFile && file.extension == "cbs" }.forEach { file ->
            val input = CharStreams.fromPath(file.toPath())
            val lexer = CBSLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = CBSParser(tokens)
            val root = parser.root()
            parseTrees[file.name] = root
        }
    }

    // Iteratively finds all dependencies of the provided language and keeps track of passes.
    private fun findDependencies() {
        if (languageIndex == null) return

        var newDependenciesFound: Boolean

        do {
            val previousDependencies = dependencies.toSet()

            newDependenciesFound = false
            parseTrees.forEach { name, root ->
                val depVisitor = DependencyVisitor(index, dependencies, builtins)
                depVisitor.visit(root)
            }

            if (dependencies != previousDependencies) {
                newDependenciesFound = true
            }
        } while (newDependenciesFound)
    }


    private fun generateObjects() {
        parseTrees.forEach { name, root ->
            val cbsFile = CBSFile(name, root, index)
            cbsFile.visit(root)
            val fileObjects = cbsFile.objects
            if (fileObjects.isNotEmpty()) {
                files[name] = cbsFile
                fileObjects.forEach { obj -> globalObjects[obj.name] = obj }
            }
        }
    }

    private fun generateCode() {
        val outputDir = Path.of("src/main/kotlin/fctruffle/generated")
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir)
        }

        files.forEach { (name, file) ->
            val code = file.generateCode()
            val filePath = outputDir.resolve("${toClassName(name)}.kt").pathString
//            File(filePath).writeText(code)
        }
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: <program> <cbsDir> [<languageIndex>]")
                return
            }

            val cbsDirPath = Path.of(args[0])
            if (!Files.exists(cbsDirPath) || !Files.isDirectory(cbsDirPath)) {
                println("Invalid directory: ${cbsDirPath.pathString}")
                return
            }
            val cbsDir = cbsDirPath.toFile()

            val languageIndex = if (args.size > 1) {
                val languageIndexPath = Path.of(args[1])
                if (!Files.exists(languageIndexPath) || !Files.isRegularFile(languageIndexPath)) {
                    println("Invalid file: ${languageIndexPath.pathString}")
                    return
                }
                languageIndexPath.toFile()
            } else {
                println("No language index provided. Generating code for all Funcons in CBS dir.")
                null
            }
            if (languageIndex != null) println("Generating code for Funcons in ${languageIndex.name}")

            val truffleGen = TruffleGen(cbsDir, languageIndex)
            truffleGen.process()
        }
    }
}
