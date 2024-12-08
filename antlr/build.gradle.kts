plugins {
    kotlin("jvm") version "2.0.21"
    java
    antlr
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4-runtime:4.13.2")
    antlr("org.antlr:antlr4:4.13.2")
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

// Common configuration for generating ANTLR sources
fun registerAntlrGrammarTask(name: String, grammarFileName: String, outputDirPath: String) {
    tasks.register<JavaExec>(name) {
        group = "antlr"
        description = "Generates ANTLR sources for $grammarFileName"

        val grammarFile = file("src/main/antlr/$grammarFileName")
        val outputDir = file("src/main/java/$outputDirPath")

        inputs.file(grammarFile)
        outputs.dir(outputDir)

        classpath = configurations["antlr"]
        mainClass.set("org.antlr.v4.Tool")
        args = listOf(
            grammarFile.absolutePath, "-visitor", // Generate visitor classes
            "-long-messages", // Use long messages for errors
            "-o", outputDir.absolutePath // Explicit absolute output directory
        )
    }
}

registerAntlrGrammarTask("generateFCTGrammar", "FCT.g4", "fct")
registerAntlrGrammarTask("generateCBSGrammar", "CBS.g4", "cbs")

// Aggregate task to run both generation tasks
tasks.register("generateAllGrammars") {
    dependsOn("generateFCTGrammar", "generateCBSGrammar")
    group = "antlr"
    description = "Generates ANTLR sources for all grammars"
}

// Ensure compilation depends on grammar generation
tasks.compileJava {
    dependsOn("generateAllGrammars")
}

// Disable the default ANTLR source generation
tasks.named("generateGrammarSource").configure {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    dependsOn("generateAllGrammars")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.compileKotlin {
    dependsOn("generateAllGrammars")
}
