package fctruffle.grammartests

class ParserException(fileName: String, errors: List<String>) : RuntimeException(
    "Errors in file $fileName:\n${errors.joinToString("\n")}"
)