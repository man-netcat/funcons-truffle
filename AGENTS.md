# Working in this repository

Notes for AI agents and new contributors. Everything here was learned by getting
it wrong; please don't repeat the mistakes.

## Read the surrounding code before writing any

This is the single most important rule, and the one most often skipped.

Before adding a file, function or test, look at the two or three nearest
neighbours and match what they do. A 30-second `ls` or `rg` of the directory
prevents a rename-and-rewrite of ninety files.

Concretely, in this repo:

- **Test function names** follow the convention in
  `antlr/src/test/kotlin/GrammarTest.kt` and
  `kt_source/.../parsetests/BooleansTest.kt` (the latter is now `trufflegen`):
  a short noun phrase naming the thing under test, plus a `Test` suffix —
  `cbsGrammarTest`, `notNodeTest`, `andNodeTest`. Plain identifiers.
- **Do not use Kotlin backtick function names** (`` fun `does a thing`() ``).
  Every one of the 93 that existed in `cbslang` and `fctinterpreter` was written
  by an agent, not by the repository's author, and all of them have been
  renamed. Long names also truncate in IDE completion, make
  `--tests '...'` awkward to pass, and do not survive `rg`.
- **Test names stay short; the reasoning goes in a comment** above the test or
  on the first line of its body. A test that encodes a surprising fact needs the
  explanation next to the assertion, because that is where a future reader
  looks — and because a name cannot be renamed without losing it. A comment
  that merely restates the name is noise; don't add one.

## Verify provenance before claiming something is a convention

Before telling the user "that matches the existing style" or "that breaks the
convention", check the history:

```bash
git log --oneline --reverse --format='%H %an %s' | head -30
git log --diff-filter=A --format='%an' -- <file>   # who added it
git show <commit>:<path>                            # what it looked like then
```

Almost all recent commits in this repository were written by an agent using the
repository owner's git identity, so `--author` tells you nothing about who wrote
which line. `git log --oneline -- <path>` and reading the content at a given
commit is the only reliable signal. An agent once cited its own 93 backtick
test names back at the owner as "the existing repo convention"; every one of
them turned out to be the agent's own.

## When you are wrong, say so and move on

If the user corrects something, the useful response is a short acknowledgement
and a fix, not a fresh justification of the original choice. Do not re-argue a
decision that has already been overturned, and do not apologise at length.

## Ground claims in a command

Every status claim in this repository should be backed by something runnable.
Prefer reporting "478 tests, 0 failures, 1 skipped" over "the suite passes", and
name the command. If a number is unknown, say it is unknown rather than
estimating.

Note that a green suite can hide known breakage, because some tests *pin* a bug
in order to document it. `fctinterpreter`'s `TestMinijava` has a
`stopsAfterLines` map that asserts the exact truncated output of a program that
stops part-way; it is currently empty because the eight programs that used to be in
it now run to completion, but the mechanism is there to pin the next one that
breaks. Read a test's KDoc before quoting its result as a success.

## Commands

```bash
./gradlew test                                        # whole suite
./gradlew :cbslang:test                               # CBS language front-end
./gradlew :fctinterpreter:test                         # interpreter
./gradlew :cbslang:run --args="../CBS-beta/Languages-beta"   # survey the corpus
./gradlew :cbslang:run --args="--write-disambiguation out ../CBS-beta/Languages-beta"
./gradlew :fctinterpreter:benchmark                    # time the config corpus
```

Per-test results are in `<module>/build/test-results/test/*.xml`; the console
output of the interpreter is noisy enough that reading the XML is faster.

## What the benchmark says

`interpreter.Benchmark` runs the 161 `.config` programs two ways and reports the
best of five attempts per program. That is not enough to make the *total* a
measurement on its own: a whole run varies by around 20% here, which is wider
than most changes being made. Take the median of at least three full runs, and
compare medians, before believing that anything got faster.

Measured on this machine after `9c1e26d`, medians of three runs each:

| | total | median | p90 |
| --- | --- | --- | --- |
| cold, a fresh `Context` per program | 1.0s | 5.0ms | 12.5ms |
| shared, one `Context` re-parsed per program | 480ms | 1.8ms | 5.8ms |

Reusing one `Context` removes about half the total. That is the headroom, and it
is mostly engine setup rather than funcon reduction, so it is the number to beat
before optimising anything inside the rewriter. The slowest programs are the same
either way, and are all class and pattern work rather than I/O or `print`.

Because the spread is that wide, a change has to be worth more than a few percent
to be visible here at all. Replacing the per-class reflection in `NodeMeta` with
metadata `trufflegen` writes (see `NodeMetasTest`, which checks the two agree for
every generated class) changed nothing measurable: the same three runs, medians
1.0s cold and 474ms shared. Reflection is not what the corpus spends its time on.
The reflection that is left is `NodeFactory`'s `Class.forName` per node at parse
time, which is on the cold path and is the next thing worth looking at.


The same run reports a correctness fact that the timing does not: whether
**every program survives a second evaluation in the same `Context`.** Evaluating an
identical source twice does not re-parse — polyglot caches the `CallTarget` by
source name and content — so the second run reduces an AST the first run has
already consumed. It used not to: 153 of the 161 programs diverged, because
`SequenceNode.popFirst` and `AtomicNode`'s `s0`/`s1` reassign `@Children`,
`TermNode.getCopy` mutates a `copyCounter` on the node it is copying, and
`reduce`/`reduceComputations` call `Node.replace` and `Node.insert`, which do real
surgery on the adopted AST. Reduction now returns the reduced subterm and lets the
caller rebuild, so a cached AST is reusable and the run reports that it survives.

The removal of `Node.replace` and `Node.insert` also fixed the eight `TestMinijava`
programs that used to stop part-way through their output. A step variable is
reduced in place, so reduction has to write the reduced subterm back into the
term being walked, and `replace` was doing that by mutating the AST instead.

The corpus has stuck programs, which is why the benchmark folds a failure into the
result instead of aborting; the suite records those as known divergences.

## Layout

| Module | What it does |
| --- | --- |
| `antlr` | Hand-written ANTLR grammars: `CBS.g4` (funcon libraries only), `FCT.g4` (funcon terms) |
| `trufflegen` | Generates `fctlang`'s funcon node classes from `CBS-beta/Funcons-beta` at build time |
| `fctlang` | The funcon library and Truffle runtime. `src/main/kotlin/generated/` is generated, do not edit |
| `fctinterpreter` | Evaluates funcon terms; owns the regression suites |
| `cbslang` | Reads the CBS *language definitions* and their SDF3 disambiguation, writes an ANTLR grammar |
| `fctfiles`, `CBS-beta` | Vendored corpora (submodules) |

Gradle runs tests with the module directory as the working directory, which is
why tests reach the vendored tree through `../`.

## What `cbslang` produces

`cbslang` reads a CBS language definition and its SDF3 disambiguation, and writes an
ANTLR 4 grammar for it. All five languages in the vendored corpus
(IMP, MiniJava, OCaml Light, SIMPLE, SL) generate a grammar the real ANTLR tool
accepts, the generated Java compiles, and programs of the language parse.
`GeneratedParserTest` runs the tool and the Java compiler rather than reading the
text, because a fault like a rule named after a target-language keyword only shows
up there.

The disambiguation is applied, not just recorded. An SDF3 `context-free priorities`
step becomes the position of the alternatives it names, because ANTLR gives a
left-recursive alternative the precedence of its position in the rule; `left` and
`right` become `<assoc=left>` and `<assoc=right>`. `miniJavaPrecedenceTest` checks
the resulting trees, since a grammar that keeps the declared order still parses
every program and gets `&&` binding tightest instead of loosest.

Two parts of a disambiguation have no equivalent in ANTLR and are written as
comments rather than guessed at: `non-assoc` (there is no option for it) and the
`avoid` and `prefer` flags, which are for a concrete syntax tree rather than a
parser. They show up in the generated file so the gap is visible.

Not implemented: layout is excluded by skipping a global `LAYOUT` token, so
adjacency and compound tokens are not modelled; there is no concrete syntax tree
and no AST, so `cbslang` output is not yet consumable by an interpreter.

## How `trufflegen` is checked

`fctlang/src/main/kotlin/generated/` is gitignored, so a change to `trufflegen`
that alters the output is invisible to `git status` and to review. `verify.sh`
regenerates the corpus and diffs it against a snapshot:

```bash
./trufflegen/verify.sh /path/to/baseline
```

The baseline is 44 files. Any refactor of `trufflegen` has to leave that diff
empty — running the generator and seeing it succeed is not evidence of anything,
because a generator that emits different code still emits compiling code. Review
the diff with `diff -ru` before re-cutting it, and commit first: the snapshot is
not in git, so re-cutting it is the only record that the output changed on
purpose.

The unit tests cover the two things a refactor can quietly break: `FunconTest`
checks that every way of naming a funcon in the grammar resolves to the same
name, aliases and parameters, and `RegistryTest` checks alias lookup and the
subtype index. They are not a substitute for the diff.

The generated `reduce` bodies are assembled in `FunconObject.reduction()`, in the
order the pieces have to be evaluated: the rules are built first, because building
them is what binds the variables the prologue reads. Reordering those steps
produces valid Kotlin that is wrong.

