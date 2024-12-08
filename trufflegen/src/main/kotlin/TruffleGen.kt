package main

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import cbs.CBSLexer
import cbs.CBSParser
import cbs.CBSParser.RootContext
import main.exceptions.DetailedException
import main.objects.Object
import main.visitors.IndexVisitor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

val globalObjects: MutableMap<String, Object?> = mutableMapOf()

class TruffleGen(private val cbsDir: File, private val outputDir: File) {
    private val files: MutableMap<String, CBSFile> = mutableMapOf()
    private var parseTrees: MutableMap<String, RootContext> = mutableMapOf()

    fun process() {
        // First generate the parse trees for all .cbs files
        generateParseTrees()

        // Generate classes that hold data for all funcons, datatypes, entities, etc...
        generateObjects()

        // Verify all objects are accounted for
        verifyObjects()

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
            parseTrees[file.name] = root
        }
    }

    private fun generateObjects() {
        parseTrees.forEach { (name, root) ->
            println("Generating objects for file $name...")
            val cbsFile = CBSFile(name, root)
            cbsFile.visit(root)
            val fileObjects = cbsFile.objects
            if (fileObjects.isNotEmpty()) {
                files[name] = cbsFile
                globalObjects.putAll(fileObjects)
            }
        }
    }

    private fun verifyObjects() {
        parseTrees.forEach { (name, root) ->
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

    private fun generateCode() {
        val outputDirPath = Path.of(outputDir.path)
        if (!Files.exists(outputDirPath)) {
            Files.createDirectories(outputDirPath)
        }

        files.forEach { (name, file) ->
            println("Generating code for file $name...")
            val code = file.generateCode()
            println(code)
            val fileNameWithoutExtension = name.removeSuffix(".cbs")
            val filePath = File(outputDir, "$fileNameWithoutExtension.kt")
            filePath.writeText(code)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 2) {
                println("Usage: <program> <cbsDir> <outputDir>")
                return
            }

            val cbsDirPath = Path.of(args[0])
            if (!Files.exists(cbsDirPath) || !Files.isDirectory(cbsDirPath)) {
                println("Invalid directory: ${cbsDirPath.pathString}")
                return
            }
            val cbsDir = cbsDirPath.toFile()

            val outputDirPath = Path.of(args[1])
            if (!Files.exists(outputDirPath) || !Files.isDirectory(outputDirPath)) {
                println("Invalid directory: ${outputDirPath.pathString}")
                return
            }
            val outputDir = outputDirPath.toFile()

            val truffleGen = TruffleGen(cbsDir, outputDir)
            truffleGen.process()
        }
    }
}
