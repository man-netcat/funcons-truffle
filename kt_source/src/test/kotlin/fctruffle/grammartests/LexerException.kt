package fctruffle.grammartests

class LexerException(fileName: String, errors: List<String>) : RuntimeException(
    "Errors in file $fileName:\n${errors.joinToString("\n")}"
)