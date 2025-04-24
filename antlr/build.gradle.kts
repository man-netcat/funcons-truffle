import dependencies.Deps

plugins {
    kotlin("jvm") version "2.1.0"
    java
    antlr
}

dependencies {
    antlr(Deps.antlrRuntime)
    antlr(Deps.antlrTool)
    implementation(Deps.kotlinStdLib)
    testImplementation(kotlin("test-junit5"))

    testImplementation(Deps.junitJupiter)
}

tasks.test {
    useJUnitPlatform()
}

// Common function for ANTLR source generation
fun registerAntlrGrammarTask(name: String, grammarFileName: String, outputDirPath: String) {
    tasks.register<JavaExec>(name) {
        group = "antlr"
        description = "Generates ANTLR sources for $grammarFileName"

        val grammarFile = file(rootDir.resolve("antlr/src/main/antlr/$grammarFileName"))
        val outputDir = file(rootDir.resolve("antlr/src/main/java/$outputDirPath"))

        inputs.file(grammarFile)
        outputs.dir(outputDir)

        classpath = configurations["antlr"]
        mainClass.set("org.antlr.v4.Tool")
        args = listOf(
            grammarFile.absolutePath,
            "-visitor",
            "-long-messages",
            "-o", outputDir.absolutePath
        )
    }
}

registerAntlrGrammarTask("generateFCTGrammar", "FCT.g4", "fct")
registerAntlrGrammarTask("generateCBSGrammar", "CBS.g4", "cbs")

tasks.register("generateAllGrammars") {
    dependsOn("generateFCTGrammar", "generateCBSGrammar")
    group = "antlr"
    description = "Generates ANTLR sources for all grammars"
}

tasks.compileJava {
    dependsOn("generateAllGrammars")
}

tasks.named("generateGrammarSource").configure {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    dependsOn("generateAllGrammars")
}
