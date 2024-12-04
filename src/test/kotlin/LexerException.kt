import java.io.File

class LexerException(file: File, errors: List<String>) : RuntimeException(
    "Errors in file ${file.name}:\n${errors.joinToString("\n")}"
)