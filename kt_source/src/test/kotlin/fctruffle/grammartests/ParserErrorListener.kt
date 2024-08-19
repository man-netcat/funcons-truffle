package fctruffle.grammartests

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

class ParserErrorListener(private val errorCollector: MutableList<String>) : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    ) {
        val errorMsg = "Parser error at line $line:$charPositionInLine - $msg"
        errorCollector.add(errorMsg)
    }
}