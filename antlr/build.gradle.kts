plugins {
    kotlin("jvm") version "2.1.0"
    java
    antlr
}

repositories {
    mavenCentral()
}

dependencies {
    antlr(libs.antlr.runtime)
    antlr(libs.antlr.tool)
    implementation(libs.kotlin.stdlib)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

val generatedSrcDir = file("/home/rick/workspace/thesis/funcons-truffle/antlr/src/main/java")

fun registerAntlrGrammarTask(name: String, grammarFileName: String) {
    val grammarFile = projectDir.resolve("src/main/antlr/$grammarFileName")
    val grammarBaseName = grammarFile.nameWithoutExtension.lowercase()
    val outputDir = generatedSrcDir.resolve(grammarBaseName)

    tasks.register<JavaExec>(name) {
        group = "antlr"
        description = "Generates ANTLR sources for $grammarFileName"

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

    sourceSets["main"].java.srcDir(outputDir)
    sourceSets["main"].kotlin.srcDir(outputDir)
}

registerAntlrGrammarTask("generateFCTGrammar", "FCT.g4")
registerAntlrGrammarTask("generateCBSGrammar", "CBS.g4")

tasks.register("generateAllGrammars") {
    dependsOn("generateFCTGrammar", "generateCBSGrammar")
    group = "antlr"
    description = "Generates all ANTLR sources"
}

tasks.named("compileKotlin") {
    dependsOn("generateAllGrammars")
}

tasks.compileJava {
    dependsOn(tasks.named("compileKotlin"))
}

tasks.named("generateGrammarSource").configure { enabled = false }

sourceSets["main"].java.srcDir(generatedSrcDir)
sourceSets["main"].kotlin.srcDir(generatedSrcDir)

tasks.named("compileTestKotlin") {
    dependsOn(tasks.named("generateTestGrammarSource"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn("generateAllGrammars")
    dependsOn("generateTestGrammarSource")
}
