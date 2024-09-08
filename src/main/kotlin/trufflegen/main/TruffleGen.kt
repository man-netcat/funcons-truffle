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

class TruffleGen(private val cbsDir: File, private val languageIndex: File?) {
    private val files: MutableMap<String, CBSFile> = mutableMapOf()
    private var index: List<String>? = mutableListOf<String>()

    fun process() {
        index = buildIndex()
        generateObjects()
        generateCode()
    }

    fun buildIndex() = if (languageIndex != null) {
        val input = CharStreams.fromPath(languageIndex.toPath())
        val lexer = CBSLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = CBSParser(tokens)
        val root = parser.root()
        val indexVisitor = IndexVisitor()
        indexVisitor.visit(root)
        indexVisitor.index
    } else {
        null
    }

    private fun generateObjects() {
        cbsDir.walkTopDown().filter { file -> file.isFile && file.extension == "cbs" }.forEach { file ->
            println("\nProcessing data for file: ${file.name}")
            val input = CharStreams.fromPath(file.toPath())
            val lexer = CBSLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = CBSParser(tokens)
            val root = parser.root()
            val cbsFile = CBSFile(file.name, root, index)
            cbsFile.visit(root)
            val fileObjects = cbsFile.objects
            if (fileObjects.isNotEmpty()) {
                files[file.name] = cbsFile
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
                println("No language index provided. Proceeding without it.")
                null
            }

            val truffleGen = TruffleGen(cbsDir, languageIndex)
            truffleGen.process()
        }
    }

}
