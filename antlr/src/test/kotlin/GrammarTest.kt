import org.antlr.v4.runtime.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

import cbs.CBSParser
import cbs.CBSLexer

import fct.FCTParser
import fct.FCTLexer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrammarTest {
    private fun processFile(file: File, lexerClass: Class<out Lexer>, parserClass: Class<out Parser>) {
        val input = CharStreams.fromFileName(file.path)
        val lexer = lexerClass.getConstructor(CharStream::class.java).newInstance(input)
        val tokens = CommonTokenStream(lexer)
        val parser = parserClass.getConstructor(TokenStream::class.java).newInstance(tokens)

        val lexerErrorCollector = mutableListOf<String>()
        lexer.addErrorListener(LexerErrorListener(file, lexerErrorCollector))

        val parserErrorCollector = mutableListOf<String>()
        parser.removeErrorListeners()
        parser.addErrorListener(ParserErrorListener(file, parserErrorCollector))

        val ruleMethod = parserClass.getMethod("root")
        ruleMethod.invoke(parser)

        if (lexerErrorCollector.isNotEmpty()) {
            throw LexerException(file, lexerErrorCollector)
        }
        if (parserErrorCollector.isNotEmpty()) {
            throw ParserException(file, parserErrorCollector)
        }
    }

    @ParameterizedTest(name = "Test CBS file: {0}")
    @MethodSource("cbsFilesProvider")
    fun cbsGrammarTest(file: File) {
        processFile(file, CBSLexer::class.java, CBSParser::class.java)
    }

    @ParameterizedTest(name = "Test FCT file: {0}")
    @MethodSource("fctFilesProvider")
    fun fctGrammarTest(file: File) {
        processFile(file, FCTLexer::class.java, FCTParser::class.java)
    }

    companion object {
        @JvmStatic
        fun cbsFilesProvider(): Stream<File> {
            return getFilesStream("cbs")
        }

        @JvmStatic
        fun fctFilesProvider(): Stream<File> {
            return getFilesStream("config")
        }

        private fun getFilesStream(fileExtension: String): Stream<File> {
            val directory = File("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")

            return directory.walk().filter { it.isFile && it.extension == fileExtension }.toList().stream()
        }
    }
}

