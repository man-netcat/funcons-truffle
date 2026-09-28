package language

class TestExpectations(
    val resultTerm: String? = null,
    val store: String? = null,
    val standardOut: List<String>? = null
) {
    val hasExpectations: Boolean
        get() = resultTerm != null || store != null || standardOut != null

    companion object {
        val NONE = TestExpectations()
    }
}

class ComparisonReport(
    val hasExpectations: Boolean,
    val passed: Boolean,
    val failures: List<String>
) {
    fun describe(): String = when {
        !hasExpectations -> "no expectations declared"
        passed -> "all declared expectations met"
        else -> failures.joinToString(System.lineSeparator())
    }

    companion object {
        val NOT_CHECKED = ComparisonReport(hasExpectations = false, passed = true, failures = emptyList())
    }
}

object ValueCanonicalizer {
    fun canonical(value: String?): String? {
        if (value == null) return null
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '"' || c == '\'') {
                val quote = c
                out.append(c)
                i++
                while (i < value.length) {
                    val d = value[i]
                    out.append(d)
                    i++
                    if (d == '\\' && i < value.length) {
                        out.append(value[i])
                        i++
                    } else if (d == quote) {
                        break
                    }
                }
            } else if (!c.isWhitespace()) {
                out.append(c)
                i++
            } else {
                i++
            }
        }
        return out.toString()
    }
}

sealed interface CbsValue {
    data class Atom(val text: String) : CbsValue
    data class Seq(val elements: List<CbsValue>) : CbsValue
    data class Set(val elements: List<CbsValue>) : CbsValue
    data class Map(val entries: List<Pair<CbsValue, CbsValue>>) : CbsValue
}

object CbsStructure {
    private const val ARROW = "|->"

    fun parse(raw: String): CbsValue {
        val text = ValueCanonicalizer.canonical(raw).orEmpty()
        return parseValue(text)
    }

    fun structurallyEqual(a: CbsValue, b: CbsValue): Boolean = when {
        a is CbsValue.Atom && b is CbsValue.Atom -> a.text == b.text
        a is CbsValue.Seq && b is CbsValue.Seq ->
            a.elements.size == b.elements.size &&
                a.elements.zip(b.elements).all { (x, y) -> structurallyEqual(x, y) }
        a is CbsValue.Set && b is CbsValue.Set -> unorderedEqual(a.elements, b.elements)
        a is CbsValue.Map && b is CbsValue.Map -> mapsEqual(a.entries, b.entries)
        else -> false
    }

    private fun parseValue(text: String): CbsValue {
        if (text.isEmpty()) return CbsValue.Atom(text)
        if (text.startsWith("\"")) return CbsValue.Atom(text)
        if (text.length >= 2 && text.startsWith("[") && text.endsWith("]")) {
            return CbsValue.Seq(splitTopLevel(text.substring(1, text.length - 1)).map { parseValue(it) })
        }
        if (text.length >= 7 && text.startsWith("tuple(") && text.endsWith(")")) {
            return CbsValue.Seq(splitTopLevel(text.substring(6, text.length - 1)).map { parseValue(it) })
        }
        if (text.length >= 2 && text.startsWith("{") && text.endsWith("}")) {
            val body = text.substring(1, text.length - 1)
            if (body.isEmpty()) return CbsValue.Set(emptyList())
            val parts = splitTopLevel(body)
            if (parts.all { indexOfTopLevelArrow(it) >= 0 }) {
                return CbsValue.Map(
                    parts.map { part ->
                        val idx = indexOfTopLevelArrow(part)
                        parseValue(part.substring(0, idx)) to parseValue(part.substring(idx + ARROW.length))
                    }
                )
            }
            return CbsValue.Set(parts.map { parseValue(it) })
        }
        return CbsValue.Atom(text)
    }

    private fun unorderedEqual(a: List<CbsValue>, b: List<CbsValue>): Boolean {
        if (a.size != b.size) return false
        val consumed = BooleanArray(b.size)
        for (element in a) {
            var matched = false
            for (j in b.indices) {
                if (!consumed[j] && structurallyEqual(element, b[j])) {
                    consumed[j] = true
                    matched = true
                    break
                }
            }
            if (!matched) return false
        }
        return true
    }

    private fun mapsEqual(a: List<Pair<CbsValue, CbsValue>>, b: List<Pair<CbsValue, CbsValue>>): Boolean {
        if (a.size != b.size) return false
        val consumed = BooleanArray(b.size)
        for ((key, value) in a) {
            var matched = false
            for (j in b.indices) {
                val (otherKey, otherValue) = b[j]
                if (!consumed[j] && structurallyEqual(key, otherKey) && structurallyEqual(value, otherValue)) {
                    consumed[j] = true
                    matched = true
                    break
                }
            }
            if (!matched) return false
        }
        return true
    }

    private fun splitTopLevel(body: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '"' || c == '\'') {
                val quote = c
                current.append(c)
                i++
                while (i < body.length) {
                    val d = body[i]
                    current.append(d)
                    i++
                    if (d == '\\' && i < body.length) {
                        current.append(body[i])
                        i++
                    } else if (d == quote) {
                        break
                    }
                }
                continue
            }
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString())
                current.setLength(0)
                i++
                continue
            }
            current.append(c)
            i++
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    private fun indexOfTopLevelArrow(text: String): Int {
        var depth = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '"' || c == '\'') {
                val quote = c
                i++
                while (i < text.length) {
                    val d = text[i]
                    i++
                    if (d == '\\' && i < text.length) i++ else if (d == quote) break
                }
                continue
            }
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (depth == 0 && text.startsWith(ARROW, i)) return i
            i++
        }
        return -1
    }
}

object ExpectationCheck {
    fun compare(
        expectations: TestExpectations,
        actualResultTerm: String,
        actualStore: String,
        actualStandardOut: List<String>
    ): ComparisonReport {
        if (!expectations.hasExpectations) return ComparisonReport.NOT_CHECKED

        val failures = mutableListOf<String>()

        expectations.resultTerm?.let { expected ->
            if (!matches(expected, actualResultTerm)) {
                failures += "result-term: expected <$expected> but got <$actualResultTerm>"
            }
        }

        expectations.store?.let { expected ->
            if (!matches(expected, actualStore)) {
                failures += "store: expected <$expected> but got <$actualStore>"
            }
        }

        expectations.standardOut?.let { expected ->
            if (expected.size != actualStandardOut.size) {
                failures += "standard-out: expected ${expected.size} value(s) " +
                    "[${expected.joinToString(", ")}] but got ${actualStandardOut.size} " +
                    "[${actualStandardOut.joinToString(", ")}]"
            } else {
                expected.forEachIndexed { index, element ->
                    val actual = actualStandardOut[index]
                    if (!matches(element, actual)) {
                        failures += "standard-out[$index]: expected <$element> but got <$actual>"
                    }
                }
            }
        }

        return ComparisonReport(
            hasExpectations = true,
            passed = failures.isEmpty(),
            failures = failures
        )
    }

    private fun matches(expected: String, actual: String): Boolean =
        CbsStructure.structurallyEqual(CbsStructure.parse(expected), CbsStructure.parse(actual))
}
