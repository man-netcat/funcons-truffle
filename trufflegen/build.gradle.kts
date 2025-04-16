@file:OptIn(ExperimentalPathApi::class)

import dependencies.Vars
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

plugins {
    kotlin("jvm") version "2.1.0"
    java
    application
}

dependencies {
    implementation(project(":antlr"))
}

tasks.compileKotlin {
    dependsOn(":antlr:generateGrammarSource")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("main.TruffleGen")
}

tasks.named<JavaExec>("run") {
    val generated = Paths.get(Vars.GENERATEDPATHSTR)

    generated.deleteRecursively()
    Files.createDirectories(generated)

    args = listOf(
        Vars.CBSFILEPATH,
        Vars.GENERATEDPATHSTR,
    )
}