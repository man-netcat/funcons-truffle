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

fun registerAntlrGrammarTask(name: String, grammarFileName: String, outputDirName: String) {
    val outputDir = layout.buildDirectory.dir("generated-src/antlr/$outputDirName")

    tasks.register<JavaExec>(name) {
        group = "antlr"
        description = "Generates ANTLR sources for $grammarFileName"

        val grammarFile = projectDir.resolve("src/main/antlr/$grammarFileName")

        inputs.file(grammarFile)
        outputs.dir(outputDir)

        classpath = configurations["antlr"]
        mainClass.set("org.antlr.v4.Tool")
        args = listOf(
            grammarFile.absolutePath,
            "-visitor",
            "-long-messages",
            "-o", outputDir.get().asFile.absolutePath
        )
    }

    sourceSets["main"].java.srcDir(outputDir)
    sourceSets["main"].kotlin.srcDir(outputDir)
}

registerAntlrGrammarTask("generateFCTGrammar", "FCT.g4", "fct")
registerAntlrGrammarTask("generateCBSGrammar", "CBS.g4", "cbs")

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

sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated-src/antlr/fct"))
sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated-src/antlr/cbs"))

sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generated-src/antlr/fct"))
sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generated-src/antlr/cbs"))

tasks.named("compileTestKotlin") {
    dependsOn(tasks.named("generateTestGrammarSource"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn("generateAllGrammars")
    dependsOn("generateTestGrammarSource")
}
