package trufflegen.main

import java.io.File

fun isFileOfType(file: File, fileType: String = "cbs"): Boolean {
    return file.isFile && file.extension == fileType
}

fun toClassName(input: String): String {
    return (input.split("-").joinToString("") { word ->
        word.replaceFirstChar { it.uppercase() }
    }) + "Node"
}

fun makeBody(content: String, indentLevel: Int = 1): String {
    val indent = "    ".repeat(indentLevel)
    return content.lines().joinToString("\n") { "$indent$it" }
}

fun makeFunction(name: String, returnType: String, parameters: List<Pair<String, String>>, body: String): String {
    val params = parameters.joinToString(", ") { "${it.first}: ${it.second}" }
    val functionHeader = "fun $name($params): $returnType {"
    val indentedBody = makeBody(body)
    return "$functionHeader\n$indentedBody\n}"
}

fun makeIfStatement(condition: String, trueBranch: String, falseBranch: String? = null): String {
    val ifStatement = "if ($condition) {\n${makeBody(trueBranch)}\n}"
    return if (falseBranch != null) {
        "$ifStatement else {\n${makeBody(falseBranch)}\n}"
    } else {
        ifStatement
    }
}

fun makeForLoop(variable: String, range: String, body: String): String {
    val loopHeader = "for ($variable in $range) {"
    val indentedBody = makeBody(body)
    return "$loopHeader\n$indentedBody\n}"
}

fun makeVariable(name: String, type: String, value: String): String {
    return "val $name: $type = $value"
}

fun makeClass(
    name: String,
    properties: List<Pair<String, String>>,
    functions: List<String>,
    annotations: List<String> = emptyList(),
): String {
    val annotationsStr = if (annotations.isNotEmpty()) {
        annotations.joinToString("\n") { "@$it" } + "\n"
    } else {
        ""
    }

    val propertiesStr = properties.joinToString("\n") { "val ${it.first}: ${it.second}" }

    val classBody = listOf(propertiesStr, *functions.toTypedArray()).joinToString("\n\n")
    val indentedClassBody = makeBody(classBody)

    return "${annotationsStr}class $name {\n$indentedClassBody\n}"
}

fun makeWhenExpression(
    expression: String,
    branches: List<Pair<String, String>>,
    elseBranch: String? = null,
): String {
    val branchesStr = branches.joinToString("\n") {
        "    ${it.first} -> ${it.second}"
    }
    val elseStr = if (elseBranch != null) {
        "\n    else -> $elseBranch"
    } else {
        ""
    }
    return "when ($expression) {\n$branchesStr$elseStr\n}"
}