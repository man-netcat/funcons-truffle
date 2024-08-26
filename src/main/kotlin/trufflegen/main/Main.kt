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

fun main() {
    val directoryPath = Path.of("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")
    val directory = directoryPath.toFile()

    if (!validateDirectory(directory)) return

    // First pass: parse each file and store the parse tree
    val fileTreeMap = generateParseTrees(directory)

    // Second pass: build objects
    val funconObjects = buildFuncons(fileTreeMap)

    // Fourth pass: generate and write code
//    generateCode(objects)
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

    directory.walkTopDown().filter { file -> isFileOfType(file, "cbs") }.forEach { file ->
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
): MutableMap<String, ObjectDataContainer> {
    val objectBuilderVisitor = ObjectBuilderVisitor()
    val objects = mutableMapOf<String, ObjectDataContainer>()

    fileTreeMap.forEach { (file, tree) ->
        println("Processing data for file: ${file.name}")
        tree?.let {
            val data = objectBuilderVisitor.visit(it)
            data?.forEach { obj ->
                obj.file = file
                objects[obj.name] = obj
            }
        }
    }

    return objects
}

private fun generateCode(objects: Map<String, ObjectDataContainer>) {
    val outputDir = Path.of("src/main/kotlin/fctruffle/generated")
    if (!Files.exists(outputDir)) {
        Files.createDirectories(outputDir)
    }

    objects.forEach { (name, obj) ->
        println("Generating file for object: $name (file: ${obj.name})")
        val code = obj.generateCode()
        println(code)
        val filePath = outputDir.resolve("${obj.nodeName}.kt").pathString
//        writeFile(filePath, code)
    }
}
