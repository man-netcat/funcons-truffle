import antlr.CBSLexer
import antlr.CBSParser
import java.io.File
import java.util.stream.Stream

class CBSGrammarTest() : GrammarTest<CBSLexer, CBSParser>() {
    override val lexerClass: Class<CBSLexer> = CBSLexer::class.java
    override val parserClass: Class<CBSParser> = CBSParser::class.java
    override fun filesProvider(): Stream<File> {
        val directory = File("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")

        return directory.walk().filter { it.isFile && it.extension == "cbs" }.toList().stream()
    }
}