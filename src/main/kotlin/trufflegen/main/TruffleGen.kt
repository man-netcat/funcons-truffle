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

class TruffleGen(private val cbsDir: File) {
    private val files: MutableMap<String, CBSFile> = mutableMapOf()
    private var parseTrees: MutableMap<String, RootContext> = mutableMapOf<String, RootContext>()

    fun process() {
        generateParseTrees()
        generateObjects()
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
        parseTrees.forEach { name, root ->
            println("Generating objects for file $name...")
            val cbsFile = CBSFile(name, root)
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
            println("Generating code for file $name...")
            val code = file.generateCode()
            println(code)
            val fileNameWithoutExtension = name.removeSuffix(".cbs")
            val filePath = outputDir.resolve("$fileNameWithoutExtension.kt").pathString
            File(filePath).writeText(code)
        }
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.isEmpty()) {
                println("Usage: <program> <cbsDir>")
                return
            }

            val cbsDirPath = Path.of(args[0])
            if (!Files.exists(cbsDirPath) || !Files.isDirectory(cbsDirPath)) {
                println("Invalid directory: ${cbsDirPath.pathString}")
                return
            }
            val cbsDir = cbsDirPath.toFile()

            val truffleGen = TruffleGen(cbsDir)
            truffleGen.process()
        }
    }
}
