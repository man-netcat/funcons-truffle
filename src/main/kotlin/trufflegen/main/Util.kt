package trufflegen.main

import org.antlr.v4.runtime.tree.ParseTree
import trufflegen.antlr.CBSParser.*

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
    typeParams: List<Pair<String, String?>> = emptyList(),
    superClass: Triple<String, List<String>, List<String>>? = null,
    interfaces: List<String> = emptyList(),
): String {
    val annotationsStr = if (annotations.isNotEmpty()) {
        annotations.joinToString("\n") { str -> "@$str" } + "\n"
    } else {
        ""
    }

    val constructorStr = constructorArgs.joinToString(", ")

    val propertiesStr = properties.joinToString("\n") { "val ${it.first}: ${it.second}" }

    val typeParamStr = if (typeParams.isNotEmpty()) "<" + typeParams.joinToString { (metavar, superClass) ->
        if (superClass != null) "$metavar : $superClass" else metavar
    } + "> " else ""

    val superClassStr = if (superClass != null) {
        val (superClassName, superClassTypeParams, superClassArgs) = superClass
        makeFun(superClassName, superClassTypeParams, superClassArgs)
    } else ""

    val inheritanceStr = when {
        superClassStr.isNotEmpty() && interfaces.isNotEmpty() -> ": $superClassStr, ${interfaces.joinToString(", ")}"
        superClassStr.isNotEmpty() -> ": $superClassStr"
        interfaces.isNotEmpty() -> ": ${interfaces.joinToString(", ")}"
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

fun makeFun(name: String, typeParams: List<String>, params: List<String>): String {
    val superclassTypeParamStr = if (typeParams.isNotEmpty()) "<${typeParams.joinToString(", ")}>" else ""
    return "$name$superclassTypeParamStr(${params.joinToString(", ")})"
}

fun entityMap(name: String) = "entityMap[\"${name}\"]"

fun todoExecute(returnStr: String) = makeExecuteFunction("TODO(\"Implement me\")", returnStr)

tailrec fun extractAndOrExprs(
    expr: ExprContext, definitions: List<ExprContext> = emptyList(),
): List<ExprContext> = when (expr) {
    is OrExpressionContext -> extractAndOrExprs(expr.lhs, definitions + expr.rhs)
    is AndExpressionContext -> extractAndOrExprs(expr.lhs, definitions + expr.rhs)
    else -> definitions + expr
}

fun makeVariableStepName(varStep: VariableStepContext): String =
    varStep.varname.text + "p".repeat(varStep.squote().size)

fun emptySuperClass(name: String): Triple<String, List<String>, List<String>> = Triple(name, emptyList(), emptyList())

fun buildTypeRewrite(type: Type, nullable: Boolean = true): String {
    val rewriteVisitor = TypeRewriteVisitor(type, nullable)
    val rewritten = rewriteVisitor.visit(type.expr)
    return rewritten
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
        is MutableEntityDefinitionContext -> paramHelper(obj.lhsParams)

        else -> throw DetailedException("Unexpected funcon type: ${obj::class.simpleName}, ${obj.text}")
    }
}

fun extractArgs(expr: ParseTree): List<ExprContext> {
    fun argHelper(args: List<ExprContext>): List<ExprContext> {
        return args.map { arg ->
            when (arg) {
                is TypeExpressionContext -> if (arg.value != null) arg.value else arg.type
                else -> arg
            }
        }
    }

    fun paramHelper(params: ParamsContext?): List<ExprContext> {
        if (params == null) return emptyList()
        return params.param().map { param ->
            if (param.value != null) param.value else param.type
        }
    }

    return when (expr) {
        is FunconDefinitionContext -> paramHelper(expr.params())
        is TypeDefinitionContext -> paramHelper(expr.params())
        is DatatypeDefinitionContext -> paramHelper(expr.params())

        is FunconExpressionContext -> {
            when (val args = expr.args()) {
                is NoArgsContext -> emptyList()
                is SingleArgsContext -> argHelper(listOf(args.expr()))
                is MultipleArgsContext -> argHelper(args.exprs().expr())
                is ListIndexExpressionContext -> argHelper(args.indices.expr())
                else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
            }
        }

        else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}, ${expr.text}")
    }
}

fun argsToParams(expr: ParseTree): List<Param> {
    fun argHelper(args: List<ExprContext>): List<Param> {
        return args.mapIndexed { i, arg ->
            when (arg) {
                is TypeExpressionContext -> Param(i, arg.value, arg.type)
                else -> Param(i, arg, null)
            }
        }
    }

    return when (expr) {
        is FunconExpressionContext -> {
            when (val args = expr.args()) {
                is NoArgsContext -> emptyList()
                is SingleArgsContext -> argHelper(listOf(args.expr()))
                is MultipleArgsContext -> argHelper(args.exprs().expr())
                is ListIndexExpressionContext -> argHelper(args.indices.expr())
                else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
            }
        }

        else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}, ${expr.text}")
    }
}
