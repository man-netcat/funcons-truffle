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
    name: String, returnType: String, parameters: List<Triple<String, String, String>>, body: String,
): String {
    val params = parameters.joinToString(", ") { "${it.first} ${it.second}: ${it.third}" }
    val functionHeader = "fun $name($params): $returnType {"
    val content = makeBody(body)
    return "$functionHeader\n$content\n}"
}

fun makeIfStatement(vararg conditions: Pair<String, String>, elseBranch: String? = null): String {
    val ifStatements = conditions.mapIndexed { index, (condition, block) ->
        (if (index == 0) "" else "else ") + "if ($condition) {\n${makeBody(block)}\n}"
    }.joinToString(" ")

    val elseStatement = elseBranch?.let { " else {\n${makeBody(it)}\n}" } ?: ""

    return "$ifStatements$elseStatement"
}

fun makeExecuteFunction(content: String, returns: String): String {
    return makeFunction("execute", returns, listOf(Triple("private val", "frame", "VirtualFrame")), content)
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
    typeParams: List<String> = emptyList(),
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

    // Generate the type parameters string, but omit if empty
    val typeParamStr = if (typeParams.isNotEmpty()) {
        "<" + typeParams.joinToString() + "> "
    } else {
        ""
    }

    // Combine constructor and properties
    val classHeader = if (constructorStr.isNotEmpty()) {
        "class $typeParamStr$name($constructorStr) {\n"
    } else {
        "class $typeParamStr$name {\n"
    }

    // Generate the class body with properties and content, filter out empty sections
    val classBody = listOf(propertiesStr, content).filter { it.isNotBlank() }.joinToString("\n\n")
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

fun makeTypeAlias(aliasName: String, targetType: String): String {
    return "typealias $aliasName = $targetType"
}

