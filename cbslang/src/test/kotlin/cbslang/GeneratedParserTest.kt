package cbslang

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.tool.ANTLRMessage
import org.antlr.v4.tool.ANTLRToolListener
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/**
 * The generated grammar, checked against the real ANTLR tool.
 *
 * The rest of [GrammarWriterTest] reads the text the writer produces. That cannot find
 * a name that ANTLR rejects, a token that shadows another, or a literal that ends the
 * string it is written in, because each of those only shows up when the tool reads
 * the file. This generates the parser, compiles it, and runs it over programs of the
 * language, which is the only way to know the grammar is a grammar.
 *
 * The tools are the project's own, from the `test` classpath, so the version is the
 * one the rest of the build uses.
 */
class GeneratedParserTest {

    /**
     * Every language in the corpus has to produce a grammar the tool accepts.
     *
     * The corpus is the requirement, not IMP: a language whose grammar cannot be
     * written is a limitation to record, and one that is only discovered by hand is a
     * regression waiting to happen. The list of accepted languages can only shrink
     * deliberately.
     */
    @Test
    fun everyLanguageIsAcceptedTest() {
        val rejected = mutableListOf<String>()
        for (def in Languages.readAll(Paths.get("CBS-beta", "Languages-beta"))) {
            if (def.name == "<failed>") continue
            val name = grammarName(def)
            val result = generate(def, name)
            if (result.problems.isNotEmpty()) rejected += "${def.name}: ${result.problems.first()}"
        }
        assertTrue(rejected.isEmpty()) { "ANTLR rejected these grammars:\n  " + rejected.joinToString("\n  ") }
    }

    /**
     * The IMP programs the grammar has to accept.
     *
     * These are the shapes of the language rather than random strings: a declaration
     * with two variables, both forms of `if`, a `while`, nested parentheses, a negative
     * number, a boolean operator and a `!`. IMP's `aexp` has `+` and `/` but no `-`, and
     * it has no function declarations, so a program using either would be testing a
     * language the corpus does not define.
     */
    @Test
    fun impAcceptsItsOwnLanguageTest() {
        val programs = listOf(
            "int x ; x = 1 + 2 ;",
            "int x ; { }",
            "int x ; if (1 <= 2) { x = 1 ; } else { x = 2 ; }",
            "int x ; if (1 <= 2) { x = 1 ; }",
            "int x ; while (1 <= x) { x = x / 1 ; }",
            "int x , y ; x = 1 ; y = 2 ;",
            "int x ; x = (1 + 2) / 3 ;",
            "int x ; x = -5 ;",
            "int x ; x = 1 + 2 + 3 ;",
            "int x ; if (!(1 <= 2) && 3 <= 4) { x = 1 ; }",
            "int x ; { x = 1 ; { x = 2 ; } x = 3 ; }",
        )
        assertParses("IMP", programs)
    }

    /**
     * A keyword is not an identifier, so a program that uses one as a name is not a
     * program. `int if ;` has to be rejected; a grammar whose identifier rule also
     * matched the keyword would accept it and then fail at run time. The keyword token
     * has to come before the identifier for this, since both match two characters.
     */
    @Test
    fun keywordIsNotAnIdentifierTest() {
        val accepted = assertRejected("IMP", listOf("int if ; x = 1 ;", "int x ; if = 1 ;"))
        assertTrue(accepted.isEmpty()) { "a keyword lexed as an identifier: $accepted" }
    }

    /**
     * The same for MiniJava, whose reserved words are its own list rather than the
     * language's symbols, and which also rejects them from a declared name.
     */
    @Test
    fun miniJavaKeywordsAreRejectedTest() {
        val accepted = assertRejected(
            "MiniJava",
            listOf("class A { public static void main ( String [ ] args ) { int class ; } }"),
        )
        assertTrue(accepted.isEmpty()) { "a keyword lexed as an identifier: $accepted" }
    }

    /**
     * MiniJava's identifier and integer literal are separate tokens, and the
     * distinction is only kept if the character set an identifier is built from is
     * inlined into it rather than lexed as a token of its own. A `digit` token would
     * shadow `integer-literal`, and `1` would lex as something no parser rule accepts.
     */
    @Test
    fun miniJavaTokensAreDistinguishedTest() {
        assertParses(
            "MiniJava",
            listOf(
                "class A { public static void main ( String [ ] args ) { System . out . println ( 1 ) ; } }",
                "class A { public static void main ( String [ ] args ) { a = 1 ; } }",
                "class A { public static void main ( String [ ] args ) { if ( 1 < 2 ) { } else { } } }",
                "class A { public static void main ( String [ ] args ) { while ( 1 < 2 ) { } } }",
                "class A { public static void main ( String [ ] args ) { a [ 1 ] = 2 ; } }",
                "class A { public static void main ( String [ ] args ) { System . out . println ( new B ( ) ) ; } }",
                "class A { public static void main ( String [ ] args ) { System . out . println ( new int [ 3 ] ) ; } }",
                // A program is a main class followed by class declarations, and a
                // class body is a name, then every variable, then every method, so a
                // bare class or a stray statement is not a program.
                "class A { public static void main ( String [ ] args ) { a = 1 ; } } class B { int y ; boolean b ; }",
                "class A { public static void main ( String [ ] args ) { a = 1 ; } }"
                    + " class B { int y ; public int f ( ) { return 1 ; } }",
                "class A { public static void main ( String [ ] args ) { a = 1 ; } } class B extends A { int y ; }",
            ),
        )
    }

    /** SL is the language with the most alternatives per nonterminal, and a call syntax. */
    @Test
    fun slAcceptsItsOwnLanguageTest() {
        assertParses(
            "SL",
            listOf(
                "function f ( x ) { return x ; }",
                "function f ( x ) { x = 1 ; }",
                "function f ( x ) { while ( 1 <= 2 ) { return 3 ; } }",
                "function f ( x ) { g ( 1 , 2 ) ; }",
                "function f ( x ) { x . y = 1 ; }",
                "function f ( x ) { break ; }",
                "function f ( x ) { if ( 1 == 2 ) { 3 ; } else { continue ; } }",
            ),
        )
    }

    /**
     * A keyword set is a list of words rather than a token, and its words have to
     * reach the lexer as tokens of their own. SL's `expr` includes an identifier and
     * a function call, and neither is distinguishable if `id` also matched `let`.
     */
    @Test
    fun slIdentifiersAreNotKeywordsTest() {
        assertParses("SL", listOf("function f ( x ) { let ; }", "function f ( x ) { let = 1 ; }"))
        val accepted = assertRejected("SL", listOf("function f ( x ) { 1 let 2 ; }"))
        assertTrue(accepted.isEmpty()) { "a keyword lexed as an identifier: $accepted" }
    }

    /**
     * A program that uses the whole language at once.
     *
     * The other MiniJava cases each cover one construct, so a grammar that lost a
     * rule, or made two rules overlap, would still pass them. This is a single
     * program exercising every production the corpus declares: three classes with
     * inheritance, fields, a local variable, a `void`-less method list, array
     * allocation and indexing, `length`, object creation, a call with arguments,
     * `this`, `if`/`else`, `while`, a nested block, and every operator the
     * `expression` nonterminal has.
     *
     * It is written to the grammar the corpus states rather than to the language it
     * resembles, which is why three things read oddly: a `main` body is a single
     * `statement` and so the body is one block, a call needs a receiver because
     * `type` has no `void` and a bare call is not an alternative of `expression`, and
     * `>` is not an operator, only `<` is.
     */
    @Test
    fun miniJavaParsesAWholeProgramTest() {
        val program = """
            class Main {
              public static void main ( String [ ] args ) {
                {
                  a = 1 + 2 * 3 - 4 ;
                  b [ 0 ] = 5 ;
                  c = new int [ 10 ] ;
                  d = c . length ;
                  if ( a < 0 && ! false ) { a = a - 1 ; } else { a = 0 ; }
                  while ( a < 10 ) { a = a + 1 ; b [ a ] = a ; }
                  p = new Point ( ) ;
                  q = p . getX ( ) ;
                  r = p . scale ( a , b [ 0 ] ) ;
                  System . out . println ( r ) ;
                  g = ( 1 + 2 ) * ( 3 + 4 ) ;
                  h = true ;
                }
              }
            }
            class Point {
              int x ;
              int y ;
              boolean live ;
              public int getX ( ) { return x ; }
              public int setX ( int v ) { int t ; t = v ; return t ; }
              public int scale ( int k , int o ) { return x * k + o ; }
            }
            class Sub extends Point {
              int extra ;
              public int sum ( ) { return extra + this . getX ( ) ; }
            }
        """.trimIndent()
        assertParses("MiniJava", listOf(program))
    }

    /**
     * The `expression` alternatives have to come out in the order the corpus states
     * them, because that order is what says which operator binds tighter.
     *
     * A grammar that parses every program and still gets this wrong is the failure
     * this test exists for: MiniJava writes `&&` first in its declaration and last in
     * its `context-free priorities`, so a generator that keeps the declared order
     * makes `&&` the tightest operator in the language. Each case is therefore
     * checked as a tree rather than as a program, since every one of them parses
     * either way.
     */
    @Test
    fun miniJavaPrecedenceTest() {
        val cases = mapOf(
            // `*` is a step of its own above `+` and `-`, which share one.
            "1 + 2 * 3" to "(expression (expression 1) + (expression (expression 2) * (expression 3)))",
            "1 * 2 + 3" to "(expression (expression (expression 1) * (expression 2)) + (expression 3))",
            // `!` is a construct step above `*`, so it reaches further than one operand.
            "! 1 * 2" to "(expression (expression ! (expression 1)) * (expression 2))",
            // `&&` is the last step and so the loosest, and `<` is the step above it.
            "1 < 2 && 3 < 4" to
                "(expression (expression (expression 1) < (expression 2)) && " +
                "(expression (expression 3) < (expression 4)))",
            "1 && 2 < 3" to "(expression (expression 1) && (expression (expression 2) < (expression 3)))",
            // `{left}` on the arithmetic operators, so a chain folds to the left.
            "1 + 2 + 3" to "(expression (expression (expression 1) + (expression 2)) + (expression 3))",
        )
        val wrong = mutableListOf<String>()
        for ((expression, expected) in cases) {
            val tree = miniJavaStatementTree(expression)
            if (!tree.contains(expected)) {
                wrong += "$expression: wanted $expected, got $tree"
            }
        }
        assertTrue(wrong.isEmpty()) {
            "these expressions did not get the precedence the corpus states:\n  " +
                wrong.joinToString("\n  ")
        }
    }

    /**
     * The parse tree of `a = <expression> ;` in a minimal MiniJava program.
     *
     * Only the statement is of interest, and the program around it is the least that
     * is a program, so that a difference in the tree is the expression's.
     */
    private fun miniJavaStatementTree(expression: String): String {
        val def = language("MiniJava")
        val grammar = grammarName(def)
        val result = generate(def, grammar)
        assertTrue(result.problems.isEmpty()) { "MiniJava does not compile: ${result.problems}" }
        val loader = compile(result.classes, result.output)
        val program = "class A { public static void main ( String [ ] args ) { a = $expression ; } }"
        val lexer = loader.loadClass("${grammar}Lexer").getConstructor(CharStream::class.java)
            .newInstance(CharStreams.fromString(program)) as org.antlr.v4.runtime.Lexer
        val parser = loader.loadClass("${grammar}Parser").getConstructor(TokenStream::class.java)
            .newInstance(CommonTokenStream(lexer)) as Parser
        val start: Method = parser.javaClass.methods.first { it.name == "start" && it.parameterCount == 0 }
        val tree = start.invoke(parser) as ParseTree
        return tree.toStringTree(parser).replace(Regex("\\s+"), " ")
    }

    private fun grammarName(def: LanguageDef): String =
        DisambiguationFiles.slug(def.name).replaceFirstChar { it.uppercase() }

    private fun language(name: String): LanguageDef =
        Languages.readAll(Paths.get("CBS-beta", "Languages-beta")).first { it.name == name }

    /**
     * Runs the programs and returns the ones that were accepted.
     *
     * A parse that reports an error is a rejection even when the parser recovered, so
     * the count is the measure rather than whether an exception was thrown: ANTLR
     * repairs most mistakes and returns a tree.
     */
    private fun assertParses(name: String, programs: List<String>) {
        val def = language(name)
        val grammar = grammarName(def)
        val result = generate(def, grammar)
        assertTrue(result.problems.isEmpty()) { "$name does not compile: ${result.problems}" }

        val loader = compile(result.classes, result.output)
        val accepted = programs.filter { errors(loader, grammar, it) > 0 }
        assertTrue(accepted.isEmpty()) {
            "$name rejected these valid programs:\n  " + accepted.joinToString("\n  ") { "$it (${errors(loader, grammar, it)} errors)" }
        }
    }

    private fun assertRejected(name: String, programs: List<String>): List<String> {
        val def = language(name)
        val grammar = grammarName(def)
        val result = generate(def, grammar)
        assertTrue(result.problems.isEmpty()) { "$name does not compile: ${result.problems}" }
        val loader = compile(result.classes, result.output)
        return programs.filter { errors(loader, grammar, it) == 0 }
    }

    /**
     * The number of errors parsing one program, or a large number if the parser
     * classes cannot be found, so that a failure to load reads as a rejection rather
     * than as a pass.
     */
    private fun errors(loader: URLClassLoader, grammar: String, program: String): Int {
        // The generated constructor takes `CharStream`, the supertype of `CharStreams`,
        // so the lookup has to name that rather than the factory used to build it.
        val lexer = loader.loadClass("${grammar}Lexer").getConstructor(CharStream::class.java)
            .newInstance(CharStreams.fromString(program))
        val tokens = CommonTokenStream(lexer as org.antlr.v4.runtime.Lexer)
        val parser = loader.loadClass("${grammar}Parser").getConstructor(TokenStream::class.java)
            .newInstance(tokens) as Parser
        val counter = Counter()
        parser.removeErrorListeners()
        parser.addErrorListener(counter)
        val start: Method = parser.javaClass.methods.first { it.name == "start" && it.parameterCount == 0 }
        val tree = try {
            start.invoke(parser) as ParseTree
        } catch (e: InvocationTargetException) {
            return 1
        }
        // A parser that inserted a missing token to recover still built a tree.
        val recovered = tree.toStringTree(parser).contains("missing")
        return counter.count + if (recovered) 1 else 0
    }

    private class Counter : org.antlr.v4.runtime.BaseErrorListener() {
        var count = 0
        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String?,
            e: RecognitionException?,
        ) {
            count++
        }
    }

    private class Generated(
        val problems: List<String>,
        val output: Path,
        val classes: List<Path>,
    )

    /**
     * Writes the grammar, runs the ANTLR tool over it, and collects what it said.
     *
     * The tool writes to standard error as well as to files, so both are captured:
     * a warning about a name that collides is the only sign of some of the faults
     * this is here to catch.
     */
    private fun generate(def: LanguageDef, grammar: String): Generated {
        val dir = Files.createTempDirectory("cbslang-$grammar")
        val file = dir.resolve(GrammarWriter.fileName(def, grammar))
        Files.writeString(file, GrammarWriter.write(def, grammar))

        val tool = org.antlr.v4.Tool(arrayOf("-o", dir.toString(), file.toString()))
        val messages = mutableListOf<String>()
        // The tool's own default listener writes to standard error, which the test
        // output would interleave with Gradle's, so it is replaced with one that
        // records. An error and a warning are both kept: a warning about a name that
        // collides in the generated code is the only sign of some faults.
        tool.removeListeners()
        tool.addListener(
            object : ANTLRToolListener {
                // The interface is inconsistent: `info` takes a string while the two
                // others take a message.
                override fun info(msg: String) = Unit
                override fun error(msg: ANTLRMessage) {
                    messages += "error: " + msg.toString()
                }

                override fun warning(msg: ANTLRMessage) {
                    messages += "warning: " + msg.toString()
                }
            },
        )
        tool.processGrammarsOnCommandLine()

        val sources = Files.walk(dir).use { stream ->
            stream.filter { it.toString().endsWith(".java") }.toList()
        }
        // The tool counts errors it did not report through the listener, so the count
        // is the authority and the messages are the detail.
        val problems = if (tool.numErrors > 0) {
            listOf("${tool.numErrors} error(s)") + messages.filter { it.startsWith("error") }
        } else {
            messages
        }
        return Generated(problems, dir, sources)
    }

    /**
     * Compiles the generated sources and loads them.
     *
     * ANTLR's output is Java, so the tool checking it has to be the Java compiler as
     * well: a rule named after a keyword of the target language parses as a grammar
     * and then fails here.
     */
    private fun compile(sources: List<Path>, output: Path): URLClassLoader {
        val classes = output.resolve("classes")
        Files.createDirectories(classes)
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("no Java compiler is available to check the generated parser")
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val units = compiler.getStandardFileManager(diagnostics, null, null)
            .getJavaFileObjectsFromFiles(sources.map { it.toFile() })
        val options = listOf(
            "-nowarn",
            "-cp",
            System.getProperty("java.class.path"),
            "-d",
            classes.toString(),
        )
        val task = compiler.getTask(null, null, diagnostics, options, null, units)
        assertTrue(task.call()) {
            "the generated parser does not compile:\n" +
                diagnostics.diagnostics.joinToString("\n") { it.toString() }
        }
        return URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader)
    }
}
