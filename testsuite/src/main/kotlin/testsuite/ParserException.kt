package testsuite

import java.io.File

class ParserException(file: File, errors: List<String>) : RuntimeException(
    "Errors in file ${file.name}:\n${errors.joinToString("\n")}"
)