package main

import cbs.CBSParser.*
import main.dataclasses.Param
import main.dataclasses.RewriteData
import main.dataclasses.Type
import main.exceptions.DetailedException
import main.exceptions.StringNotFoundException
import main.objects.Object
import main.visitors.TypeRewriteVisitor
import objects.FunconObject
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

    result.append("fun $name(${parameters.joinToString(", ")}): $returnType {\n")
    result.append(makeBody(body))
    result.append("\n}")

    return result.toString()
}

fun makeExecuteFunction(content: String, returns: String): String =
    makeFunction("execute", returns, listOf(makeParam("", "frame", "VirtualFrame")), content, listOf("override"))

fun todoExecute(returnStr: String) = makeExecuteFunction("TODO(\"Implement me\")", returnStr)

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

fun makeForLoop(variable: String, range: String, body: String): String {
    val loopHeader = "for ($variable in $range) {"
    val content = makeBody(body)
    return "$loopHeader\n$content\n}"
}

fun makeVariable(name: String, value: String, type: String? = null): String {
    return if (type != null) "val $name: $type = $value" else "val $name = $value"
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

fun makeFunCall(name: String, typeParams: Set<Pair<String, String?>>, params: List<String>): String {
    val superclassTypeParamStr = if (typeParams.isNotEmpty()) "<${typeParams.joinToString(", ") { it.first }}>" else ""
    return "$name$superclassTypeParamStr(${params.joinToString(", ")})"
}

fun getGlobal(name: String): String = makeFunCall("getGlobal", emptySet(), listOf("\"${name}\""))
fun putGlobal(name: String, value: String): String = makeFunCall("putGlobal", emptySet(), listOf("\"${name}\"", value))
fun getInScope(name: String): String = makeFunCall("getInScope", emptySet(), listOf("frame", "\"${name}\""))
fun putInScope(name: String, value: String): String =
    makeFunCall("putInScope", emptySet(), listOf("frame", "\"${name}\"", value))


tailrec fun extractAndOrExprs(
    expr: ExprContext, definitions: List<ExprContext> = emptyList(),
): List<ExprContext> = when (expr) {
    is OrExpressionContext -> extractAndOrExprs(expr.lhs, definitions + expr.rhs)
    is AndExpressionContext -> extractAndOrExprs(expr.lhs, definitions + expr.rhs)
    else -> definitions + expr
}

fun emptySuperClass(name: String): String = makeFunCall(name, emptySet(), emptyList())

fun rewriteType(type: Type, nullable: Boolean = true): String {
    val rewriteVisitor = TypeRewriteVisitor(type, nullable)
    val rewritten = rewriteVisitor.visit(type.expr)
    return rewritten
}

fun rewrite(definition: ParseTree, toRewrite: ParseTree, rewriteData: List<RewriteData> = emptyList()): String {
    fun rewriteRecursive(toRewrite: ParseTree): String {
        fun mapParamString(def: ParseTree, str: String): String {
            val paramStrs = getParamStrs(def)
            val exprMap = (paramStrs + rewriteData).associate { (arg, _, paramStr) -> Pair(arg?.text, paramStr) }
            return exprMap[str] ?: throw StringNotFoundException(str, exprMap.keys.toList())
        }

        fun rewriteFunconExpr(name: String, context: ParseTree, definition: ParseTree): String {
            val obj = globalObjects[name]!!
            val className = toClassName(name)
            val args = extractArgs(context)
            val argStr = if (!(obj.hasNullable && args.isEmpty())) {
                args.mapIndexed { i, arg ->
                    if (obj.paramsAfterVararg > 0 && i in args.size - obj.paramsAfterVararg until args.size) {
                        val paramIndex = obj.params.size - (args.size - i)
                        "p$paramIndex=${rewriteRecursive(arg)}"
                    } else rewriteRecursive(arg)
                }.joinToString()
            } else "null"
            val suffix = if (obj is FunconObject) ".execute(frame)" else ""
            return "$className($argStr)$suffix"
        }

        return when (toRewrite) {
            is FunconExpressionContext -> rewriteFunconExpr(toRewrite.name.text, toRewrite, definition)
            is ListExpressionContext -> rewriteFunconExpr("list", toRewrite, definition)
            is SetExpressionContext -> rewriteFunconExpr("set", toRewrite, definition)
            is LabelContext -> rewriteFunconExpr(toRewrite.name.text, toRewrite, definition)
            is MapExpressionContext -> {
                val pairStr = toRewrite.pairs().pair().joinToString { pair -> rewriteRecursive(pair) }
                "MapNode($pairStr)"
            }

            is TupleExpressionContext -> {
                val pairStr = toRewrite.exprs()?.expr()?.joinToString(", ") { expr ->
                    rewriteRecursive(expr)
                }
                if (pairStr != null) "arrayOf(${pairStr})" else "emptyArray()"
            }

            is SuffixExpressionContext -> {
                val suffixStr = if (toRewrite.op.text == "?") "?" else ""
                mapParamString(definition, toRewrite.text) + suffixStr
            }

            is VariableContext -> mapParamString(definition, toRewrite.text)
            is NumberContext -> "(${toRewrite.text}).toIntegersNode()"
            is StringContext -> "(${toRewrite.text}).toStringsNode()"
            is TypeExpressionContext -> rewriteRecursive(toRewrite.value)
            is NestedExpressionContext -> rewriteRecursive(toRewrite.expr())
            is PairContext -> {
                val key = rewriteRecursive(toRewrite.key)
                val value = rewriteRecursive(toRewrite.value)
                "TupleNode($key, $value)"
            }

            else -> throw IllegalArgumentException("Unsupported context type: ${toRewrite::class.simpleName}, ${toRewrite.text}")
        }
    }
    return rewriteRecursive(toRewrite)
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
            if (args.expr() !is TupleExpressionContext) {
                listOf(args.expr())
            } else emptyList()
        }

        is MultipleArgsContext -> args.exprs()?.expr() ?: emptyList()
        else -> throw DetailedException("Unexpected args type: ${args::class.simpleName}, ${args.text}")
    }
}

fun partitionArrayArgs(args: List<ExprContext?>): Pair<List<ExprContext>, List<ExprContext>> {
    return args.filterNotNull().partition { arg ->
        arg is SuffixExpressionContext || (arg is TypeExpressionContext && arg.value is SuffixExpressionContext)
    }
}

fun getParamStrs(definition: ParseTree, isParam: Boolean = false, prefix: String = ""): List<RewriteData> {
    fun makeParamStr(
        argIndex: Int, argsSize: Int, obj: Object, parentStr: String, argIsArray: Boolean = false
    ): String {
        // Calculate the number of arguments passed to the vararg
        val nVarargArgs = argsSize - (obj.params.size - 1)
        val argsAfterVararg = if (obj.varargParamIndex >= 0) argsSize - (obj.varargParamIndex + nVarargArgs) else 0

        // Prefix '*' if the argument is an array
        val starPrefix = if (argIsArray && !isParam) "*" else ""

        // Utility function to build parameter string based on provided condition
        fun buildParamString(paramIndex: Int, suffix: String = ""): String {
            return listOf(parentStr, "p$paramIndex").filterNot { it.isEmpty() }.joinToString(".") + suffix
        }

        return when {
            // Case when there is no vararg parameter (obj.varargParamIndex == -1)
            obj.varargParamIndex == -1 || argIndex in 0 until obj.varargParamIndex -> {
                starPrefix + buildParamString(argIndex)
            }

            // Case for an actual vararg parameter range
            argIndex in obj.varargParamIndex until obj.varargParamIndex + nVarargArgs -> {
                val varargRelativeIndex = argIndex - obj.varargParamIndex

                if (!argIsArray) {
                    buildParamString(obj.varargParamIndex, "[$varargRelativeIndex]")
                } else if (varargRelativeIndex != 0) {
                    starPrefix + "slice(${buildParamString(obj.varargParamIndex)}, $varargRelativeIndex)"
                } else {
                    starPrefix + buildParamString(obj.varargParamIndex)
                }
            }

            // Case for parameters after the vararg parameter
            argIndex in obj.varargParamIndex + nVarargArgs until argsSize -> {
                "TODO(\"Params after vararg not implemented\")"
            }

            else -> throw IndexOutOfBoundsException("argIndex $argIndex out of bounds.")
        }
    }

    fun extractArgsRecursive(definition: ParseTree, parentStr: String = prefix): List<RewriteData> {
        val (obj, args) = when (definition) {
            is FunconDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
            is TypeDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
            is DatatypeDefinitionContext -> globalObjects[definition.name.text]!! to extractParams(definition)
            is FunconExpressionContext -> globalObjects[definition.name.text]!! to argsToParams(definition)
            is ListExpressionContext -> globalObjects["list"]!! to argsToParams(definition)
            is SetExpressionContext -> globalObjects["set"]!! to argsToParams(definition)
            is LabelContext -> globalObjects[definition.name.text]!! to argsToParams(definition)
            else -> throw DetailedException("Unexpected definition type: ${definition::class.simpleName}, ${definition.text}")
        }

        return args.flatMapIndexed { argIndex, (arg, type) ->
            when (arg) {
                null -> {
                    // Argument is a type
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr)
                    listOf(RewriteData(type, null, newStr))
                }

                is FunconExpressionContext, is ListExpressionContext, is SetExpressionContext -> {
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr)
                    listOf(RewriteData(null, arg, newStr)) + extractArgsRecursive(arg, newStr)
                }

                is SuffixExpressionContext, is VariableContext, is NumberContext -> {
                    val argIsArray = (arg is SuffixExpressionContext && arg.op.text in listOf("+", "*"))
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr, argIsArray = argIsArray)
                    listOf(RewriteData(arg, type, newStr))
                }

                is TupleExpressionContext -> {
                    val newStr = makeParamStr(argIndex, args.size, obj, parentStr, argIsArray = true)
                    listOf(RewriteData(arg, null, newStr))
                }

                else -> throw DetailedException("Unexpected arg type: ${arg::class.simpleName}, ${arg.text}")
            }
        }
    }
    return extractArgsRecursive(definition)
}

fun makeAnnotation(isVararg: Boolean = false, isOverride: Boolean = false): String {
    val annotationBuilder = StringBuilder()

    annotationBuilder.append(if (isVararg) "@Children " else "@Child ")

    if (isOverride) annotationBuilder.append("override ")

    if (isVararg) annotationBuilder.append("vararg ")

    annotationBuilder.append("val")

    return annotationBuilder.toString()
}

fun main() {
}