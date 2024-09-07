package trufflegen.main

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import trufflegen.antlr.CBSLexer
import trufflegen.antlr.CBSParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

private val globalObjects: MutableMap<String, Object> = mutableMapOf()

class TruffleGen(private val path: File) {
    private val files: MutableMap<String, CBSFile> = mutableMapOf()

    fun process() {
        generateObjects(path)
        generateCode()
    }

    private fun generateObjects(directory: File) {
        directory.walkTopDown().filter { file -> file.isFile && file.extension == "cbs" }.forEach { file ->
            println("Generating parse tree for file: ${file.name}")
            val input = CharStreams.fromPath(file.toPath())
            val lexer = CBSLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = CBSParser(tokens)
            val root = parser.root()

            val cbsFile = CBSFile(file.name, root)

            files[file.name] = cbsFile
        }

        files.forEach { (name, file) ->
            println("\nProcessing data for file: $name")
            val root = file.root
            file.visit(root)
            val fileObjects = file.objects

            fileObjects.forEach { obj ->
                globalObjects[obj.name] = obj
            }
        }
    }


    private fun generateCode() {
        val outputDir = Path.of("src/main/kotlin/fctruffle/generated")
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir)
        }

        files.forEach { (name, file) ->
            println("\nGenerating file for: $name")
            try {
                val code = file.generateCode()
                val filePath = outputDir.resolve("${toClassName(name)}.kt").pathString
//                File(filePath).writeText(code)
            } catch (e: DetailedException) {
                println(e)
            }
        }
    }


    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val directoryPath = Path.of("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")
            val directory = directoryPath.toFile()
            if (!directory.exists() || !directory.isDirectory) {
                println("Invalid directory: ${directory.path}")
                return
            }
            val truffleGen = TruffleGen(directory)
            truffleGen.process()
        }
    }
}
