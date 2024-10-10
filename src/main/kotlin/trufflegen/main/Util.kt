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

fun makeParam(annotation: String, name: String, type: String): String {
    return if (annotation.isNotEmpty()) {
        "$annotation $name: $type"
    } else {
        "$name: $type"
    }
}


fun makeFunction(
    name: String,
    returnType: String,
    parameters: List<String>,
    body: String,
    modifiers: List<String> = emptyList(),
): String {
    val params = parameters.joinToString(", ")

    val modifierStr = if (modifiers.isNotEmpty()) {
        modifiers.joinToString(" ") + " "
    } else {
        ""
    }

    val functionHeader = "${modifierStr}fun $name($params): $returnType {"
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
    return makeFunction("execute", returns, listOf(makeParam("", "frame", "VirtualFrame")), content, listOf("override"))
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
    content: String = "",
    body: Boolean = true,
    keywords: List<String> = emptyList(),
    annotations: List<String> = emptyList(),
    constructorArgs: List<String> = emptyList(),
    properties: List<Pair<String, String>> = emptyList(),
    typeParams: Set<String> = emptySet(),
    superClass: String? = null,
    interfaces: List<String> = emptyList(),
    superClassArgs: List<String> = emptyList(),
): String {
    val annotationsStr = if (annotations.isNotEmpty()) {
        annotations.joinToString("\n") { str -> "@$str" } + "\n"
    } else {
        ""
    }

    val constructorStr = constructorArgs.joinToString(", ")

    val propertiesStr = properties.joinToString("\n") { "val ${it.first}: ${it.second}" }

    val typeParamStr = if (typeParams.isNotEmpty()) {
        "<" + typeParams.joinToString() + "> "
    } else {
        ""
    }

    val inheritanceStr = when {
        superClass != null && interfaces.isNotEmpty() -> ": $superClass(${superClassArgs.joinToString(", ")}), " + interfaces.joinToString(
            ", "
        )

        superClass != null -> ": $superClass(${superClassArgs.joinToString(", ")})"
        interfaces.isNotEmpty() -> ": " + interfaces.joinToString(", ")
        else -> ""
    }

    val keywordsStr = if (keywords.isNotEmpty()) {
        keywords.joinToString(" ") + " "
    } else {
        ""
    }

    val classHeader = if (constructorStr.isNotEmpty()) {
        "${keywordsStr}class $name$typeParamStr($constructorStr) $inheritanceStr"
    } else {
        "${keywordsStr}class $name$typeParamStr $inheritanceStr"
    }

    return if (body) {
        val classBody = listOf(propertiesStr, content).filter { it.isNotBlank() }.joinToString("\n\n")
        val indentedClassBody = makeBody(classBody)
        "${annotationsStr}${classHeader} {\n$indentedClassBody\n}"
    } else {
        "${annotationsStr}${classHeader}"
    }
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

fun makeTypeAlias(aliasName: String, targetType: String, typeParams: Set<String> = emptySet()): String {
    val typeParamStr = if (typeParams.isNotEmpty()) {
        "<" + typeParams.joinToString() + "> "
    } else {
        ""
    }
    return "typealias $aliasName$typeParamStr = $targetType"
}