package trufflegen.main

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSLexer
import trufflegen.antlr.CBSParser
import trufflegen.antlr.CBSParser.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

class TruffleGen(private val directoryPath: Path) {
    fun process() {
        val directory = directoryPath.toFile()

        if (!validateDirectory(directory)) return

        // First pass: parse each file and store the parse tree
        val fileTreeMap = generateParseTrees(directory)

        // Second pass: build objects
        val funconObjects = buildFuncons(fileTreeMap)

        // Fourth pass: generate and write code
        generateCode(funconObjects)
    }

    private fun validateDirectory(directory: File): Boolean {
        return if (!directory.exists() || !directory.isDirectory) {
            println("Invalid directory: ${directory.path}")
            false
        } else {
            true
        }
    }

    private fun generateParseTrees(directory: File): Map<File, RootContext?> {
        val fileTreeMap = mutableMapOf<File, RootContext?>()

        directory.walkTopDown().filter { file -> file.isFile && file.extension == "cbs" }.forEach { file ->
            println("Generating parse tree for file: ${file.name}")
            val tree = parseFile(file)
            fileTreeMap[file] = tree
        }

        return fileTreeMap
    }

    private fun parseFile(file: File): RootContext? {
        return try {
            val input = CharStreams.fromPath(file.toPath())
            val lexer = CBSLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = CBSParser(tokens)
            parser.root()
        } catch (e: Exception) {
            println("Failed to parse file: ${file.name}")
            null
        }
    }

    private fun buildFuncons(
        fileTreeMap: Map<File, RootContext?>
    ): MutableMap<String, DefinitionDataContainer> {
        val definitionBuilderVisitor = DefinitionBuilderVisitor()
        val objects = mutableMapOf<String, DefinitionDataContainer>()

        fileTreeMap.forEach { (file, tree) ->
            println("\nProcessing data for file: ${file.name}")
            tree?.let {
                definitionBuilderVisitor.visit(it)
                val data = definitionBuilderVisitor.getObjects()
                if (data.isEmpty()) throw Exception("No data generated")
                data.forEach { obj ->
                    obj.file = file
                    objects[obj.name] = obj
                }
            }
        }

        return objects
    }

    private fun generateCode(objects: Map<String, DefinitionDataContainer>) {
        val outputDir = Path.of("src/main/kotlin/fctruffle/generated")
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir)
        }

        objects.forEach { (name, obj) ->
            println("\nGenerating file for object: $name (file: ${obj.name})")
            val code = obj.generateCode()
            println(code)
            val filePath = outputDir.resolve("${obj.nodeName}.kt").pathString
//            writeFile(filePath, code)
        }
    }
}