package main

import cbs.CBSLexer
import cbs.CBSParser
import fct.FCTLexer
import fct.FCTParser
import main.exceptions.DetailedException
import main.objects.AlgebraicDatatypeObject
import main.objects.Object
import main.objects.TypeObject
import main.visitors.DependencyVisitor
import main.visitors.FCTFunconsVisitor
import main.visitors.IndexVisitor
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

val globalObjects: MutableMap<String, Object?> = mutableMapOf()

class TruffleGen(
    private val cbsDir: File,
    private val outputDir: File,
    private vararg val fctFiles: File
) {
    private val files: MutableMap<String, CBSFile> = mutableMapOf()
    private var cbsParseTrees = mutableMapOf<String, CBSParser.RootContext>()
    private val fctParseTrees = mutableMapOf<String, FCTParser.RootContext>()
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
        generateGraphViz()

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

        // Also optionally make parse trees for the FCT files
        fctFiles.forEach { file ->
            val input = CharStreams.fromPath(file.toPath())
            val lexer = FCTLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = FCTParser(tokens)
            val root = parser.root()
            fctParseTrees[file.name] = root
            println(root.text)
        }
    }

    private fun generateObjects() {
        cbsParseTrees.forEach { (name, root) ->
            println("Generating objects for file $name...")
            val cbsFile = CBSFile(name)
            cbsFile.visit(root)
            val fileObjects = cbsFile.objects
            if (fileObjects.isNotEmpty()) {
                files[name] = cbsFile
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
        val usedFuncons = fctParseTrees.flatMap { (name, root) ->
            val fctVisitor = FCTFunconsVisitor()
            fctVisitor.visit(root)
            fctVisitor.fileFuncons
        }.distinct()

        files.values.forEach { file ->
            file.objects.values.distinct().forEach { obj ->
                println("Generating object dependencies for object ${obj?.name}...")
                when (obj) {
                    is TypeObject -> obj.dependencies.add(globalObjects["value-types"]!!)
                    is AlgebraicDatatypeObject -> {
                        obj.dependencies.add(globalObjects["datatype-values"]!!)
                        obj.definitions.forEach { dep -> dep.dependencies.add(obj) }
                    }

                    else -> {
                        val visitor = DependencyVisitor()
                        visitor.visit(obj?.ctx)
                        val objDependencies = visitor.dependencies
                            .map { objName -> globalObjects[objName]!! }
                            .filter { dep -> dep.name != obj?.name }
                            .distinct()
                        obj?.dependencies?.addAll(objDependencies)
                    }
                }
            }
        }

        fun visitObject(obj: Object) {
            if (obj !in generatedDependencies) { // Alternative safety check
                generatedDependencies.add(obj)
                obj.dependencies.forEach { visitObject(it) }
            }
        }

        usedFuncons.forEach { name ->
            globalObjects[name]?.let { visitObject(it) }
        }
        println(generatedDependencies)
    }

    private fun generateGraphViz() {
        println("Generating Graphviz for dependencies...")
        val sb = StringBuilder("digraph G {\n")
        for (obj in generatedDependencies) {
            for (dep in obj.dependencies) {
                sb.append(
                    "  \"${obj.name}\" -> \"${dep.name}\";\n"
                )
            }
        }
        sb.append("}")
        val outputFile = File("dependencies.dot")
        outputFile.writeText(sb.toString())
        println("Graphviz has been written to ${outputFile.absolutePath}")
    }

    private fun generateCode() {
        val outputDirPath = Path.of(outputDir.path)
        if (!Files.exists(outputDirPath)) {
            Files.createDirectories(outputDirPath)
        }

        files.forEach { (name, file) ->
            println("Generating code for file $name...")
            val code = file.generateCode(generatedDependencies)
            if (code.isNotBlank()) {
                println(code)
                val fileNameWithoutExtension = name.removeSuffix(".cbs")
                val filePath = File(outputDir, "$fileNameWithoutExtension.kt")
                filePath.writeText(code)
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 2) {
                println("Usage: <program> <cbsDir> <outputDir> [included...]")
                return
            }

            val cbsDir = File(args[0])
            val outputDir = File(args[1])
            val fctFiles = args.drop(2).map { File(it) }.toTypedArray()

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

            val truffleGen = TruffleGen(cbsDir, outputDir, *fctFiles)
            truffleGen.process()
        }
    }
}
