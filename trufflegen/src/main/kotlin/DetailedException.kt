package main

open class DetailedException(
    message: String, cause: Throwable? = null
) : Exception(message, cause) {
    private val red = "\u001B[31m"
    private val reset = "\u001B[0m"

    override val message: String?
        get() {
            val stackTraceElement = this.stackTrace.firstOrNull()
            return if (stackTraceElement != null) {
                val fileName = stackTraceElement.className ?: "Unknown file"
                val lineNumber = stackTraceElement.lineNumber
                val methodName = stackTraceElement.methodName
                "${red}Exception occurred in $fileName::$methodName at line $lineNumber: ${super.message}${reset}"
            } else {
                super.message
            }
        }
}
