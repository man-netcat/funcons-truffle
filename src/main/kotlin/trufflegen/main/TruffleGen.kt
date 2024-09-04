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

class TruffleGen(private val path: File) {
    private val objects: MutableMap<String, Object> = mutableMapOf()
    private val files: MutableMap<File, RootContext?> = mutableMapOf()

    fun process() {
        // First pass: parse each file and store the parse tree
        generateParseTrees(path)

        // Second pass: build objects
        buildObjects()

        // Third pass: generate and write code
        generateCode()
    }

    private fun generateParseTrees(directory: File) {
        directory.walkTopDown().filter { file -> file.isFile && file.extension == "cbs" }.forEach { file ->
            println("Generating parse tree for file: ${file.name}")
            val input = CharStreams.fromPath(file.toPath())
            val lexer = CBSLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = CBSParser(tokens)
            files[file] = parser.root()
        }
    }

    private fun buildObjects() {
        files.forEach { (file, tree) ->
            println("\nProcessing data for file: ${file.name}")
            val objectBuilderVisitor = ObjectBuilderVisitor()
            tree?.let {
                objectBuilderVisitor.visit(it)
                val data = objectBuilderVisitor.getObjects()
                data.forEach { obj ->
                    obj.file = file
                    objects[obj.name] = obj
                }
            }
        }
    }

    private fun generateCode() {
        val outputDir = Path.of("src/main/kotlin/fctruffle/generated")
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir)
        }

        objects.forEach { (name, obj) ->
            println("\nGenerating file for object: $name (file: ${obj.file.name})")

            try {
                val code = obj.generateCode(objects)
            } catch (e: DetailedException) {
                println("Failed to build ${obj::class.simpleName}: ${name}.\n${e.message}")
            }
//            println(code)
            val filePath = outputDir.resolve("${obj.nodeName}.kt").pathString
//            writeFile(filePath, code)
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
