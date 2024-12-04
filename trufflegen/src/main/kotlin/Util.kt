package main

import org.antlr.v4.runtime.tree.ParseTree
import antlr.CBSParser.*

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

    result.append("fun $name(${parameters.joinToString(", ")}): $returnType {\n")
    result.append(makeBody(body))
    result.append("\n}")

    return result.toString()
}

fun makeIfStatement(vararg conditions: Pair<String, String>, elseBranch: String? = null): String {
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

    // main.Type parameters
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
        result.append(constructorArgs.joinToString(", "))
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

    // Body
    if (body) {
        result.append(" {\n")
        if (properties.isNotEmpty()) {
            properties.forEach { result.append("    val ${it.first}: ${it.second}\n") }
            if (content.isNotBlank()) result.append("\n")
        }
        if (content.isNotBlank()) result.append(makeBody(content))
        result.append("\n}")
    }

    return result.toString()
}

fun makeWhenExpression(
    expression: String,
    branches: List<Pair<String, String>>,
    elseBranch: String? = null,
): String {
    val result = StringBuilder()

    result.append("when ($expression) {\n")
    branches.forEach { (condition, action) ->
        result.append("    $condition -> $action\n")
    }
    if (elseBranch != null) {
        result.append("    else -> $elseBranch\n")
    }
    result.append("}")

    return result.toString()
}

fun makeTypeAlias(aliasName: String, targetType: String, typeParams: Set<Pair<String, String>> = emptySet()): String {
    val typeParamStr = if (typeParams.isNotEmpty()) {
        "<" + typeParams.joinToString(", ") { it.first } + ">"
    } else ""
    return "typealias $aliasName$typeParamStr = $targetType$typeParamStr"
}

fun makeFun(name: String, typeParams: Set<Pair<String, String>>, params: List<String>): String {
    val superclassTypeParamStr = if (typeParams.isNotEmpty()) "<${typeParams.joinToString(", ") { it.first }}>" else ""
    return "$name$superclassTypeParamStr(${params.joinToString(", ")})"
}

fun entityMap(name: String) = "main.entityMap[\"${name}\"]"

fun todoExecute(returnStr: String) = makeExecuteFunction("TODO(\"Implement me\")", returnStr)

tailrec fun extractAndOrExprs(
    expr: ExprContext, definitions: List<ExprContext> = emptyList(),
): List<ExprContext> = when (expr) {
    is OrExpressionContext -> extractAndOrExprs(expr.lhs, definitions + expr.rhs)
    is AndExpressionContext -> extractAndOrExprs(expr.lhs, definitions + expr.rhs)
    else -> definitions + expr
}

fun processVariable(varStep: VariableContext): String = varStep.varname.text + "p".repeat(varStep.squote().size)

fun emptySuperClass(name: String): String = makeFun(name, emptySet(), emptyList())

fun buildTypeRewrite(type: Type, nullable: Boolean = true): String {
    val rewriteVisitor = TypeRewriteVisitor(type, nullable)
    val rewritten = rewriteVisitor.visit(type.expr)
    return rewritten
}

fun buildRewrite(definition: ParseTree, toRewrite: ParseTree, argIndex: Int = -1): String {
    val rewriteVisitor = RewriteVisitor(definition)
    return rewriteVisitor.visit(toRewrite)
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
        is FunconExpressionContext -> argHelper(makeArgList(expr.args()))
        is ListExpressionContext -> argHelper(expr.elements?.expr() ?: emptyList())
        is SetExpressionContext -> argHelper(expr.elements?.expr() ?: emptyList())
        else -> throw DetailedException("Unexpected expression type: ${expr::class.simpleName}, ${expr.text}")
    }
}

fun makeArgList(args: ArgsContext): List<ExprContext> = when (args) {
    is NoArgsContext -> emptyList()
    is SingleArgsContext -> listOf(args.expr())
    is MultipleArgsContext -> args.exprs().expr()
    else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
}

fun getParamStrs(definition: ParseTree): List<Triple<ExprContext?, ExprContext?, String>> {
    fun makeParamStr(
        argIndex: Int, argsSize: Int, obj: Object, parentStr: String, argIsArray: Boolean = false
    ): String {
        // Calculate the number of arguments passed to the vararg
        val nVarargArgs = argsSize - (obj.paramsSize - 1)
        val argsAfterVararg = argsSize - (obj.varargParamIndex + nVarargArgs)

        // Prefix '*' if the argument is an array
        val starPrefix = if (argIsArray) "*" else ""

        // Utility function to build parameter string based on provided condition
        fun buildParamString(paramIndex: Int, suffix: String = ""): String {
            return listOf(parentStr, "p$paramIndex").filterNot { it.isEmpty() }.joinToString(".") + suffix
        }

        return starPrefix + when {
            // Case when there is no vararg parameter (obj.varargParamIndex == -1)
            obj.varargParamIndex == -1 || argIndex < obj.varargParamIndex -> {
                buildParamString(argIndex)
            }

            // Case for an actual vararg parameter range
            argIndex in obj.varargParamIndex until obj.varargParamIndex + nVarargArgs -> {
                val varargParamIndexed = argIndex - obj.varargParamIndex
                val paramIndex = obj.varargParamIndex

                if (!argIsArray) {
                    buildParamString(paramIndex, "[$varargParamIndexed]")
                } else if (argsAfterVararg > 0) {
                    "slice(${buildParamString(paramIndex)}, $argIndex, $argsAfterVararg)"
                } else if (argIndex != 0) {
                    "slice(${buildParamString(paramIndex)}, $argIndex)"
                } else {
                    buildParamString(paramIndex)
                }
            }

            // Case for parameters after the vararg parameter
            argIndex >= argsSize -> throw IndexOutOfBoundsException("argIndex $argIndex out of bounds.")

            else -> {
                // Adjust argIndex based on the number of vararg arguments
                val paramIndex = argIndex - (nVarargArgs - 1)
                require(paramIndex >= 0) { "Calculated paramIndex is negative. Check nVarargArgs." }
                buildParamString(paramIndex)
            }
        }
    }

    fun extractArgsRecursive(
        definition: ParseTree, parentStr: String = ""
    ): List<Triple<ExprContext?, ExprContext?, String>> {

        // Helper function to fetch the object and its arguments
        fun makeParams(definition: ParseTree): Pair<Object, List<Param>> {
            return when (definition) {
                is FunconDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
                is TypeDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
                is DatatypeDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
                is FunconExpressionContext -> globalObjects[definition.name.text]!! to argsToParams(definition)
                is ListExpressionContext -> globalObjects["list"]!! to argsToParams(definition)
                is SetExpressionContext -> globalObjects["set"]!! to argsToParams(definition)
                else -> throw DetailedException("Unexpected definition type: ${definition::class.simpleName}, ${definition.text}")
            }
        }

        val (obj, params) = makeParams(definition)

        return params.flatMapIndexed { argIndex, (arg, type) ->
            val argIsArray = arg is SuffixExpressionContext
            val newStr = makeParamStr(argIndex, params.size, obj, parentStr, argIsArray)

            when (arg) {
                is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext ->
                    listOf(Triple(null, arg, newStr)) + extractArgsRecursive(arg, newStr)

                else -> listOf(Triple(arg, type, newStr))
            }
        }
    }

    return extractArgsRecursive(definition)
}

fun exprToParamStr(def: ParseTree, str: String): String {
    val paramStrs = getParamStrs(def)
    val exprMap = paramStrs.associate { (arg, type, paramStr) -> Pair(arg?.text, paramStr) }
    return exprMap[str] ?: throw StringNotFoundException(str, exprMap.keys.toList())
}
