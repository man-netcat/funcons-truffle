package trufflegen.main

fun toClassName(input: String): String {
    return (input.split("-").joinToString("") { word ->
        word.replaceFirstChar { it.uppercase() }
    }) + "Node"
}

fun makeBody(content: String, indentLevel: Int = 1): String {
    val indent = "    ".repeat(indentLevel)
    return content.lines().joinToString("\n") { "$indent$it" }
}

fun makeFunction(
    name: String, returnType: String, parameters: List<Triple<String, String, String>>, body: String
): String {
    val params = parameters.joinToString(", ") { "${it.first} ${it.second}: ${it.third}" }
    val functionHeader = "fun $name($params): $returnType {"
    val content = makeBody(body)
    return "$functionHeader\n$content\n}"
}

fun makeIfStatement(condition: String, trueBranch: String, falseBranch: String? = null): String {
    val ifStatement = "if ($condition) {\n${makeBody(trueBranch)}\n}"
    return if (falseBranch != null) {
        "$ifStatement else {\n${makeBody(falseBranch)}\n}"
    } else {
        ifStatement
    }
}

fun makeExecuteFunction(content: String): String {
    return makeFunction("execute", "FCTNode", listOf(Triple("private val", "frame", "String")), content)
}

fun makeForLoop(variable: String, range: String, body: String): String {
    val loopHeader = "for ($variable in $range) {"
    val content = makeBody(body)
    return "$loopHeader\n$content\n}"
}

fun makeVariable(name: String, type: String, value: String): String {
    return "val $name: $type = $value"
}

fun makeClass(
    name: String,
    annotations: List<String>,
    constructorArgs: List<Triple<String, String, String>>,
    properties: List<Pair<String, String>>,
    content: String,
): String {
    val annotationsStr = if (annotations.isNotEmpty()) {
        annotations.joinToString("\n") { str -> "@$str" } + "\n"
    } else {
        ""
    }

    // Generate the constructor string
    val constructorStr = if (constructorArgs.isNotEmpty()) {
        constructorArgs.joinToString(", ") { "${it.first} ${it.second}: ${it.third}" }
    } else {
        ""
    }

    // Generate the properties string
    val propertiesStr = properties.joinToString("\n") { "val ${it.first}: ${it.second}" }

    // Combine constructor and properties
    val classHeader = if (constructorStr.isNotEmpty()) {
        "class $name($constructorStr) {\n"
    } else {
        "class $name {\n"
    }

    // Generate the class body with properties and content
    val classBody = listOf(propertiesStr, content).joinToString("\n\n")
    val indentedClassBody = makeBody(classBody)

    return "${annotationsStr}${classHeader}${indentedClassBody}\n}"
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

fun makeImports(packages: List<String>): String {
    return packages.joinToString("\n") { "import $it" }
}

fun makeTypeAlias(aliasName: String, targetType: String): String {
    return "typealias $aliasName = $targetType"
}

fun makeFile(
    packageName: String,
    importsStr: String,
    classStr: String,
    aliasStrs: String,
): String {
    return "package $packageName\n\n$importsStr\n\n$classStr\n\n$aliasStrs\n"
}
