package main

import cbs.CBSParser.*
import main.exceptions.DetailedException
import org.antlr.v4.runtime.tree.ParseTree

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
    val result = StringBuilder()

    if (modifiers.isNotEmpty()) {
        result.append(modifiers.joinToString(" "))
        result.append(" ")
    }

    result.append("fun $name(${parameters.joinToString()}): $returnType {\n")
    result.append(makeBody(body))
    result.append("\n}")

    return result.toString()
}

fun makeExecuteFunction(content: String, returns: String): String =
    makeFunction("execute", returns, listOf(makeParam("", "frame", "VirtualFrame")), content, listOf("override"))

fun todoExecute(returnStr: String) = makeExecuteFunction("TODO(\"Implement me\")", returnStr)

fun makeIfStatement(conditions: List<Pair<String, String>>, elseBranch: String? = null): String {
    val result = StringBuilder()

    conditions.forEachIndexed { index, (condition, block) ->
        if (index > 0) result.append("else ")
        result.append("if ($condition) {\n")
        result.append(makeBody(block))
        result.append("\n} ")
    }

    if (elseBranch != null) {
        result.append("else {\n")
        result.append(makeBody(elseBranch))
        result.append("\n}")
    }

    return result.toString()
}

fun makeForLoop(variable: String, range: String, body: String): String {
    val loopHeader = "for ($variable in $range) {"
    val content = makeBody(body)
    return "$loopHeader\n$content\n}"
}

fun makeVariable(name: String, value: String, type: String? = null, override: Boolean = false): String {
    return buildString {
        if (override) append("override ")
        append("val $name")
        if (type != null) append(": $type")
        append(" = $value")
    }
}

fun makeClass(
    name: String,
    content: String = "",
    keywords: List<String> = emptyList(),
    annotations: List<String> = emptyList(),
    constructorArgs: List<String> = emptyList(),
    properties: List<Pair<String, String>> = emptyList(),
    typeParams: Set<Pair<String, String?>> = emptySet(),
    superClass: String = "",
    interfaces: List<String> = emptyList(),
): String {
    val result = StringBuilder()

    // Annotations
    if (annotations.isNotEmpty()) {
        annotations.forEach { result.append("@$it\n") }
    }

    // Keywords
    if (keywords.isNotEmpty()) {
        result.append(keywords.joinToString(" "))
        result.append(" ")
    }

    // Class header
    result.append("class $name")

    // main.dataclasses.Type parameters
    if (typeParams.isNotEmpty()) {
        result.append("<")
        result.append(typeParams.joinToString { (metavar, superClass) ->
            if (superClass != null) "$metavar : $superClass" else metavar
        })
        result.append("> ")
    }

    // Constructor arguments
    if (constructorArgs.isNotEmpty()) {
        result.append("(")
        result.append(constructorArgs.joinToString())
        result.append(")")
    }

    // Inheritance
    val inheritance = buildList {
        if (superClass.isNotEmpty()) add(superClass)
        addAll(interfaces)
    }
    if (inheritance.isNotEmpty()) {
        result.append(" : ")
        result.append(inheritance.joinToString(" : "))
    }

    if (properties.isNotEmpty() || content.isNotBlank()) {
        result.append(" {\n")
        if (properties.isNotEmpty()) {
            properties.forEach {
                result.append("    val ${it.first}: ${it.second}\n")
            }
            if (content.isNotBlank()) result.append("\n")
        }
        if (content.isNotBlank()) result.append(makeBody(content))
        result.append("\n}")
    }

    return result.toString()
}

fun makeWhenStatement(cases: List<Pair<String, String>>, elseBranch: String? = null): String {
    val result = StringBuilder("when {\n")

    cases.forEach { (condition, block) ->
        val formattedBlock = makeBody(block, indentLevel = 0)
        if ('\n' in formattedBlock) {
            result.append("    $condition -> {\n")
            result.append(formattedBlock.prependIndent("        "))
            result.append("\n    }\n\n")
        } else {
            result.append("    $condition -> $formattedBlock\n")
        }
    }

    if (elseBranch != null) {
        val formattedElse = makeBody(elseBranch, indentLevel = 0)
        if ('\n' in formattedElse) {
            result.append("    else -> {\n")
            result.append(formattedElse.prependIndent("        "))
            result.append("\n    }\n\n")
        } else {
            result.append("    else -> $formattedElse\n")
        }
    }

    result.append("}")

    return result.toString()
}

fun getObjectName(ctx: ParseTree): String {
    return when (ctx) {
        is FunconExpressionContext -> ctx.name.text
        is FunconDefinitionContext -> ctx.name.text
        is TypeDefinitionContext -> ctx.name.text
        is DatatypeDefinitionContext -> ctx.name.text
        is ControlEntityDefinitionContext -> ctx.name.text
        is MutableEntityDefinitionContext -> ctx.name.text
        is ContextualEntityDefinitionContext -> ctx.name.text
        else -> throw DetailedException("Unexpected context type: ${ctx::class.simpleName}, ${ctx.text}")
    }
}

fun getObjectAliases(ctx: ParseTree): List<String> {
    val aliases = when (ctx) {
        is FunconDefinitionContext -> ctx.aliasDefinition()
        is TypeDefinitionContext -> ctx.aliasDefinition()
        is DatatypeDefinitionContext -> ctx.aliasDefinition()
        is ControlEntityDefinitionContext -> ctx.aliasDefinition()
        is MutableEntityDefinitionContext -> ctx.aliasDefinition()
        is ContextualEntityDefinitionContext -> ctx.aliasDefinition()
        is FunconExpressionContext -> emptyList() // Only for funcons for AlgebraicDatatypes
        else -> throw DetailedException("Unexpected context type: ${ctx::class.simpleName}, ${ctx.text}")
    }

    return aliases.mapNotNull { it.name.text }
}

fun makeTypeAlias(aliasName: String, targetType: String, typeParams: Set<Pair<String, String>> = emptySet()): String {
    val typeParamStr = if (typeParams.isNotEmpty()) {
        "<" + typeParams.joinToString(", ") { it.first } + ">"
    } else ""
    return "typealias $aliasName$typeParamStr = $targetType$typeParamStr"
}

fun makeFunCall(
    name: String,
    args: List<String> = emptyList(),
    typeParams: Set<String> = emptySet()
): String {
    val typeParamStr = if (typeParams.isNotEmpty()) {
        "<" + typeParams.joinToString(", ") + ">"
    } else ""
    return "$name$typeParamStr(${args.joinToString()})"
}

fun strStr(str: String): String = "\"${str}\""

fun getGlobalStr(name: String): String = makeFunCall("getGlobal", listOf(strStr(name)))
fun putGlobalStr(name: String, value: String): String = makeFunCall("putGlobal", listOf(strStr(name), value))
fun getInScopeStr(name: String): String = makeFunCall("getInScope", listOf("frame", strStr(name)))
fun putInScopeStr(name: String, value: String): String = makeFunCall("putInScope", listOf("frame", strStr(name), value))

tailrec fun extractAndOrExprs(
    expr: ExprContext, definitions: List<ExprContext> = emptyList(),
): List<ExprContext> = when (expr) {
    is OrExpressionContext -> extractAndOrExprs(expr.lhs, definitions + expr.rhs)
    is AndExpressionContext -> extractAndOrExprs(expr.lhs, definitions + expr.rhs)
    else -> definitions + expr
}

fun emptySuperClass(name: String): String = makeFunCall(name, emptyList())

fun makeAnnotation(isVararg: Boolean = false, isOverride: Boolean = false): String {
    val annotationBuilder = StringBuilder()

    annotationBuilder.append(if (isVararg) "@Children " else "@Child ")

    if (isOverride) annotationBuilder.append("override ")

    if (isVararg) annotationBuilder.append("vararg ")

    annotationBuilder.append("val")

    return annotationBuilder.toString()
}