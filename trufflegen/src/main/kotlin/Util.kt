package main

import cbs.CBSParser.*
import main.dataclasses.Param
import main.exceptions.DetailedException
import main.objects.AlgebraicDatatypeObject
import main.objects.Object
import main.objects.TypeObject
import objects.DatatypeFunconObject
import org.antlr.v4.runtime.tree.ParseTree

fun toCamelCase(input: String): String {
    return (input.split("-").joinToString("") { word ->
        word.replaceFirstChar { it.uppercase() }
    })
}

fun toNodeName(input: String): String = toCamelCase(input) + "Node"
fun toInterfaceName(input: String): String = toCamelCase(input) + "Interface"
fun toEntityName(input: String): String = toCamelCase(input) + "Entity"

fun toVariableName(input: String): String {
    return input.split("-").mapIndexed { index, word ->
        if (index == 0) word.lowercase() else word.replaceFirstChar { it.uppercase() }
    }.joinToString("")
}

fun makeBody(content: String, indentLevel: Int = 1): String {
    val indent = "    ".repeat(indentLevel)
    return content.lines().joinToString("\n") { "$indent$it" }
}

fun makeParam(type: String, name: String, annotation: String = "", default: String = ""): String {
    return if (annotation.isNotEmpty()) {
        if (default.isNotEmpty()) {
            "$annotation $name: $type = $default"
        } else {
            "$annotation $name: $type"
        }
    } else {
        if (default.isNotEmpty()) {
            "$name: $type = $default"
        } else {
            "$name: $type"
        }
    }
}

fun makeFunction(
    name: String,
    returnType: String,
    parameters: List<String> = emptyList(),
    body: String,
    modifiers: List<String> = emptyList(),
    indent: Int = 0,
): String {
    val result = StringBuilder()
    val indentation = "    ".repeat(indent)

    if (modifiers.isNotEmpty()) {
        result.append(modifiers.joinToString(" "))
        result.append(" ")
    }

    result.append("fun $name(${parameters.joinToString()}): $returnType {\n")

    val indentedBody = body.lines().joinToString("\n") { "$indentation    $it" }
    result.append(indentedBody.trimEnd())
    result.append("\n$indentation}")

    return result.toString()
}

fun makeIsInTypeFunction(nodeName: String, body: String): String {
    return makeFunction(
        name = "TermNode.isIn${nodeName}",
        returnType = "Boolean",
        parameters = listOf(),
        body = body,
    )
}

fun makeReduceFunction(content: String, returns: String): String =
    makeFunction(
        name = "reduceRules",
        returnType = returns,
        parameters = listOf(makeParam("VirtualFrame", "frame", "")),
        body = content,
        modifiers = listOf("override")
    )

fun todoReduce(name: String, returnStr: String) = makeReduceFunction("TODO(\"Implement me: $name\")", returnStr)

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

fun makeVariable(name: String, value: String = "", type: String = "", override: Boolean = false): String {
    return buildString {
        if (override) append("override ")
        append("val $name")
        if (type.isNotEmpty()) append(": $type")
        if (value.isNotEmpty()) append(" = $value")
    }
}

fun makeInterface(
    name: String,
    properties: List<String> = emptyList(),
    functions: List<String> = emptyList(),
    annotations: List<String> = emptyList(),
    typeParams: Set<Pair<String, String?>> = emptySet(),
    superInterfaces: List<String> = emptyList(),
): String {
    val result = StringBuilder()

    if (annotations.isNotEmpty()) {
        annotations.forEach { result.append("@$it\n") }
    }

    result.append("interface $name")

    if (typeParams.isNotEmpty()) {
        result.append("<")
        result.append(typeParams.joinToString { (metavar, superClass) ->
            if (superClass != null) "$metavar : $superClass" else metavar
        })
        result.append("> ")
    }

    if (superInterfaces.isNotEmpty()) {
        result.append(" : ")
        result.append(superInterfaces.joinToString())
    }

    if (properties.isNotEmpty() || functions.isNotEmpty()) {
        result.append(" {\n")

        properties.forEach { result.append("    $it\n") }

        functions.forEach { func ->
            result.append("    $func\n")
        }

        result.append("}")
    }

    return result.toString()
}

fun makeClass(
    name: String,
    content: String = "",
    keywords: List<String> = emptyList(),
    annotations: List<String> = emptyList(),
    constructorArgs: List<String> = emptyList(),
    properties: List<String> = emptyList(),
    typeParams: Set<Pair<String, String?>> = emptySet(),
    superClass: String = "",
    interfaces: List<String> = emptyList(),
): String {
    val result = StringBuilder()

    if (annotations.isNotEmpty()) {
        annotations.forEach { result.append("@$it\n") }
    }

    if (keywords.isNotEmpty()) {
        result.append(keywords.joinToString(" "))
        result.append(" ")
    }

    result.append("class $name")

    if (typeParams.isNotEmpty()) {
        result.append("<")
        result.append(typeParams.joinToString { (metavar, superClass) ->
            if (superClass != null) "$metavar : $superClass" else metavar
        })
        result.append("> ")
    }

    if (constructorArgs.isNotEmpty()) {
        result.append("(")
        result.append(constructorArgs.joinToString())
        result.append(")")
    }

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
            properties.forEach { prop ->
                result.append("    $prop")
            }
            if (content.isNotBlank()) result.append("\n")
        }
        if (content.isNotBlank()) result.append(makeBody(content))
        result.append("\n}")
    }

    return result.toString()
}

fun makeValueTypesCompanionObject(funBody: String): String {
    return makeCompanionObject(listOf(makeElementInFunction(funBody)))
}

fun makeCompanionObject(
    functions: List<String>,
): String {
    val result = StringBuilder()

    result.append("companion object")

    result.append(" {\n")
    functions.forEach { func ->
        result.append("    $func")
    }
    result.append("\n}")

    return result.toString()
}

fun makeElementInFunction(body: String): String = makeFunction(
    "hasElement", returnType = "Boolean", parameters = listOf(
        makeParam(name = "value", type = "TermNode")
    ),
    body = body, indent = 1
)

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
            result.append("    else -> $formattedElse")
        }
    }

    result.append("\n}")

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
    typeParams: List<String> = emptyList(),
): String {
    val typeParamStr = if (typeParams.isNotEmpty()) {
        "<" + typeParams.joinToString(", ") + ">"
    } else ""
    return "$name$typeParamStr(${args.joinToString()})"
}

fun strStr(str: String): String = "\"${str}\""

fun getGlobalStr(name: String): String = makeFunCall("getGlobal", listOf(strStr(name)))
fun putGlobalStr(name: String, value: String): String = makeFunCall("putGlobal", listOf(strStr(name), value))
fun appendGlobalStr(name: String, value: String): String = makeFunCall("appendGlobal", listOf(strStr(name), value))
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

fun makeAnnotation(
    isVararg: Boolean = false,
    isOverride: Boolean = false,
    isEntity: Boolean = false,
    isEager: Boolean,
): String {
    val annotationBuilder = StringBuilder()

    if (isEager) annotationBuilder.append("@param:Eager ")

    if (isEntity) annotationBuilder.append(if (isVararg) "@Children " else "@Child ")

    if (isOverride) annotationBuilder.append("override ")

    if (isVararg) annotationBuilder.append("vararg ")

    annotationBuilder.append("var")

    return annotationBuilder.toString()
}

fun extractParams(obj: ParseTree): List<Param> {
    fun paramHelper(params: ParamsContext?): List<Param> =
        params?.param()?.mapIndexed { i, param -> Param(i, param.value, param.type) } ?: emptyList()

    return when (obj) {
        is FunconDefinitionContext -> paramHelper(obj.params())
        is TypeDefinitionContext -> paramHelper(obj.params())
        is DatatypeDefinitionContext -> paramHelper(obj.params())
        is ControlEntityDefinitionContext -> paramHelper(obj.params())
        is ContextualEntityDefinitionContext -> paramHelper(obj.params())
        is MutableEntityDefinitionContext -> paramHelper(obj.lhs)

        else -> throw DetailedException("Unexpected funcon type: ${obj::class.simpleName}, ${obj.text}")
    }
}

fun extractArgs(expr: ParseTree): List<ExprContext> {
    return when (expr) {
        is FunconExpressionContext -> makeArgList(expr.args())
        is ListExpressionContext -> expr.elements?.expr() ?: emptyList()
        is SetExpressionContext -> expr.elements?.expr() ?: emptyList()
        is LabelContext -> if (expr.value != null) listOf(expr.value) else emptyList()
        else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}, ${expr.text}")
    }
}

fun argsToParams(expr: ParseTree): List<Param> {
    val args = extractArgs(expr)
    return args.mapIndexed { i, arg ->
        when (arg) {
            is TypeExpressionContext -> Param(i, arg.value, arg.type)
            else -> Param(i, arg, null)
        }
    }
}

fun makeArgList(args: ArgsContext): List<ExprContext> {
    return when (args) {
        is NoArgsContext -> emptyList()
        is SingleArgsContext -> {
            if (args.expr() !is SequenceExpressionContext) {
                listOf(args.expr())
            } else emptyList()
        }

        is MultipleArgsContext -> args.exprs()?.expr() ?: emptyList()
        else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
    }
}

fun partitionArgs(args: List<ExprContext?>): Pair<List<ExprContext>, List<ExprContext>> {
    return args.filterNotNull().partition { arg ->
        arg is SuffixExpressionContext || (arg is TypeExpressionContext && arg.value is SuffixExpressionContext) || arg is SequenceExpressionContext
    }
}

fun getParams(definition: ParseTree) = when (definition) {
    is FunconDefinitionContext,
    is TypeDefinitionContext,
    is ControlEntityDefinitionContext,
    is ContextualEntityDefinitionContext,
    is MutableEntityDefinitionContext,
    is DatatypeDefinitionContext,
        -> extractParams(definition)

    is FunconExpressionContext,
    is ListExpressionContext,
    is SetExpressionContext,
    is LabelContext,
        -> argsToParams(definition)

    else -> throw DetailedException("Unexpected definition type: ${definition::class.simpleName}, ${definition.text}")
}

fun getObject(definition: ParseTree): Object {
    val name = when (definition) {
        is FunconDefinitionContext -> definition.name.text
        is TypeDefinitionContext -> definition.name.text
        is DatatypeDefinitionContext -> definition.name.text
        is FunconExpressionContext -> definition.name.text
        is ListExpressionContext -> "list"
        is SetExpressionContext -> "set"
        is LabelContext -> definition.name.text
        else -> throw DetailedException("Unexpected definition type: ${definition::class.simpleName}, ${definition.text}")
    }
    return globalObjects[name]!!
}

fun makeTypeCondition(paramStr: String, typeExpr: ExprContext): String {
    val (obj, isComplement) = when (typeExpr) {
        is ComplementExpressionContext -> getObject(typeExpr.expr()) to true
        else -> getObject(typeExpr) to false
    }

    val complementStr = if (isComplement) "!" else ""
    return when {
        obj is AlgebraicDatatypeObject || obj is TypeObject
            -> "${complementStr}$paramStr.isIn${obj.camelCaseName}()"

        else -> {
            val explicitValue = if (obj is DatatypeFunconObject && obj.params.isNotEmpty()) "Value" else ""
            "$paramStr ${complementStr}is $explicitValue${obj.nodeName}"
        }
    }
}