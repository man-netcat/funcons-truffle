package testsuite

import org.antlr.v4.runtime.*
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class GrammarTest<TLexer : Lexer, TParser : Parser> {
    abstract val lexerClass: Class<TLexer>
    abstract val parserClass: Class<TParser>
    abstract fun filesProvider(): Stream<File>

    @ParameterizedTest(name = "Test Grammar file: {0}")
    @MethodSource("filesProvider")
    private fun processFile(file: File) {
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
}
